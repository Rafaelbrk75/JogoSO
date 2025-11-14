package server;

import common.ActionType;
import common.PlayerClass;
import common.Protocol;
import common.TurnCommand;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class DuelServer {

    private static final Duration ACTION_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration CLASS_TIMEOUT = Duration.ofSeconds(60);

    private final ServerSocket serverSocket;
    private final ClientSession[] clients = new ClientSession[2];
    private final SharedMemoryManager sharedMemory;
    private final ServerLogger logger = new ServerLogger("server.log");

    public DuelServer() throws IOException {
        this.serverSocket = new ServerSocket(Protocol.SERVER_PORT);
        this.sharedMemory = new SharedMemoryManager();
        log("Servidor iniciado na porta %d", Protocol.SERVER_PORT);
    }

    public static void main(String[] args) {
        try {
            DuelServer server = new DuelServer();
            server.acceptClients();
            server.runMatchLoop();
        } catch (IOException e) {
            System.err.println("Erro ao iniciar servidor: " + e.getMessage());
        }
    }

    private void acceptClients() throws IOException {
        for (int i = 0; i < 2; i++) {
            Socket socket = serverSocket.accept();
            ClientSession session = new ClientSession(socket, i + 1, this);
            clients[i] = session;
            session.start();
            log("Cliente %d conectado de %s", i + 1, socket.getRemoteSocketAddress());
        }
    }

    private void runMatchLoop() {
        try {
            while (true) {
                if (!clients[0].isConnected() || !clients[1].isConnected()) {
                    log("Um cliente desconectou antes de iniciar a partida.");
                    break;
                }
                runSingleMatch();
            }
        } finally {
            shutdown();
        }
    }

    private void runSingleMatch() {
        clients[0].sendLine("S1");
        clients[1].sendLine("S2");

        Optional<PlayerClass> class1 = waitForClassSelection(clients[0]);
        class1.ifPresent(playerClass -> broadcastClassSelection(1, playerClass));

        Optional<PlayerClass> class2 = waitForClassSelection(clients[1]);
        class2.ifPresent(playerClass -> broadcastClassSelection(2, playerClass));

        if (!class1.isPresent() || !class2.isPresent()) {
            log("Falha na seleção de classes.");
            sharedMemory.registerResult(PlayerClass.UNKNOWN);
            return;
        }

        sharedMemory.updateTurnLog("Partida iniciada");

        GameState game = new GameState(class1.get(), class2.get());
        boolean running = true;
        int manualWinner = 0;
        boolean manualTie = false;
        String manualReason = "";

        while (running) {
            if (!clients[0].isConnected() || !clients[1].isConnected()) {
                manualReason = handleDisconnect();
                manualWinner = determineWinnerOnDisconnect();
                manualTie = manualWinner == 0;
                break;
            }

            int turnFlag = (game.getTurnNumber() % 2 == 0) ? 2 : 1;
            broadcast("T" + turnFlag);
            game.startNewTurn();

            TurnCommand cmd1 = waitForCommand(clients[0], turnFlag);
            TurnCommand cmd2 = waitForCommand(clients[1], turnFlag);

            if (cmd1 == null || cmd2 == null) {
                manualWinner = determineWinnerOnDisconnect();
                if (manualWinner == 0) {
                    manualTie = true;
                    manualReason = "Ação inválida";
                } else {
                    manualReason = "Jogador " + (3 - manualWinner) + " desconectou";
                }
                break;
            }

            TurnOutcome outcome = game.applyTurn(cmd1, cmd2);
            String snapshot = Protocol.formatTurnSnapshot(
                    turnFlag,
                    game.getPlayers()[0].getHp(), game.getPlayers()[0].getMp(),
                    game.getPlayers()[0].getSkillCooldown(), game.getPlayers()[0].getHealCooldown(),
                    game.getPlayers()[1].getHp(), game.getPlayers()[1].getMp(),
                    game.getPlayers()[1].getSkillCooldown(), game.getPlayers()[1].getHealCooldown(),
                    outcome.getLogLine());
            broadcast(snapshot);
            sharedMemory.updateTurnLog(outcome.getLogLine());

            if (outcome.getWinnerId() != 0 || outcome.isTie()) {
                String endMessage = outcome.isTie()
                        ? String.format("E0|reason:%s", outcome.getReason())
                        : String.format("E%d|reason:%s", outcome.getWinnerId(), outcome.getReason());
                broadcast(endMessage);
                sharedMemory.registerResult(outcome.isTie()
                        ? PlayerClass.UNKNOWN
                        : game.getPlayers()[outcome.getWinnerId() - 1].getPlayerClass());
                running = false;
            }
        }

        if (manualWinner != 0 || manualTie) {
            String reason = manualReason.isEmpty() ? "Encerrado" : manualReason;
            if (manualTie) {
                broadcast("E0|reason:" + reason);
                sharedMemory.registerResult(PlayerClass.UNKNOWN);
            } else {
                broadcast("E" + manualWinner + "|reason:" + reason);
                sharedMemory.registerResult(game.getPlayers()[manualWinner - 1].getPlayerClass());
            }
        }
    }

    private Optional<PlayerClass> waitForClassSelection(ClientSession session) {
        BlockingQueue<String> queue = session.getQueue();
        try {
            String msg = queue.poll(CLASS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (msg == null) {
                session.sendLine("X02|Tempo excedido para escolher classe");
                return Optional.empty();
            }
            Optional<Protocol.JoinMessage> join = Protocol.parseJoin(msg);
            if (!join.isPresent() || join.get().getPlayerId() != session.getPlayerId()) {
                session.sendLine("X03|Formato de classe inválido");
                return Optional.empty();
            }
            return Optional.of(join.get().getPlayerClass());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private TurnCommand waitForCommand(ClientSession session, int turnFlag) {
        BlockingQueue<String> queue = session.getQueue();
        try {
            String msg = queue.poll(ACTION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (msg == null) {
                log("Timeout do jogador %d, defesa aplicada.", session.getPlayerId());
                TurnCommand cmd = new TurnCommand();
                cmd.setAction(ActionType.DEFEND);
                cmd.setAutoAssigned(true);
                return cmd;
            }
            if (Protocol.isQuit(msg)) {
                log("Jogador %d solicitou saída", session.getPlayerId());
                session.close();
                return null;
            }
            Optional<Protocol.ActionMessage> parsed = Protocol.parseAction(msg);
            if (!parsed.isPresent() || parsed.get().getPlayerId() != session.getPlayerId()
                    || parsed.get().getTurnFlag() != turnFlag) {
                session.sendLine("X10|Ação inválida");
                return new TurnCommand(ActionType.DEFEND, 0);
            }
            TurnCommand cmd = new TurnCommand(parsed.get().getAction(), parsed.get().getSkillIndex());
            return cmd;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private String handleDisconnect() {
        if (!clients[0].isConnected() && !clients[1].isConnected()) {
            return "Ambos desconectaram";
        }
        if (!clients[0].isConnected()) {
            return "Jogador 1 desconectou";
        }
        return "Jogador 2 desconectou";
    }

    private int determineWinnerOnDisconnect() {
        if (!clients[0].isConnected() && clients[1].isConnected()) {
            return 2;
        }
        if (!clients[1].isConnected() && clients[0].isConnected()) {
            return 1;
        }
        return 0;
    }

    private void broadcast(String message) {
        for (ClientSession client : clients) {
            if (client != null && client.isConnected()) {
                client.sendLine(message);
            }
        }
    }

    private void broadcastClassSelection(int playerId, PlayerClass playerClass) {
        broadcast("C" + playerId + playerClass.getCode());
    }

    public SharedMemoryManager getSharedMemory() {
        return sharedMemory;
    }

    public void log(String format, Object... args) {
        logger.log(format, args);
    }

    private void shutdown() {
        try {
            for (ClientSession client : clients) {
                if (client != null) {
                    client.close();
                }
            }
            serverSocket.close();
            sharedMemory.close();
            logger.close();
        } catch (IOException e) {
            // ignore
        }
    }
}

