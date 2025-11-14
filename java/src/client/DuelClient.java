package client;

import common.ActionType;
import common.PlayerClass;
import common.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class DuelClient {

    private final String host;
    private final int port;
    private final ClientState state = new ClientState();

    public DuelClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        DuelClient client = new DuelClient(host, Protocol.SERVER_PORT);
        client.run();
    }

    private void run() {
        try (Socket socket = new Socket(host, port);
             BufferedReader serverReader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter serverWriter = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader inputReader = new BufferedReader(
                     new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            System.out.println("Conectado ao servidor " + host + ":" + port);

            Thread readerThread = new Thread(() -> handleServerMessages(serverReader, serverWriter), "ServerReader");
            readerThread.start();

            while (state.isRunning()) {
                String line = inputReader.readLine();
                if (line == null) {
                    break;
                }
                processUserInput(line.trim(), serverWriter);
            }

            state.stop();
            readerThread.join();
        } catch (IOException | InterruptedException e) {
            System.out.println("Conexão encerrada: " + e.getMessage());
        }
    }

    private void handleServerMessages(BufferedReader serverReader, PrintWriter serverWriter) {
        try {
            String line;
            while ((line = serverReader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                char code = line.charAt(0);
                switch (code) {
                    case 'W':
                        int id = Character.digit(line.charAt(1), 10);
                        state.setPlayerId(id);
                        System.out.printf("Bem-vindo, jogador %d. Aguardando oponente...%n", id);
                        break;
                    case 'S':
                        int target = Character.digit(line.charAt(1), 10);
                        if (target == state.getPlayerId()) {
                            state.setAwaitingClass(true);
                            System.out.println("Selecione sua classe (G=Guerreiro, M=Mago, R=Arqueiro):");
                        }
                        break;
                    case 'T':
                        int turnFlag = Character.digit(line.charAt(1), 10);
                        state.setCurrentTurnFlag(turnFlag);
                        state.setAwaitingAction(true);
                        System.out.printf("%nTurno sinalizado (%d). Escolha A/S/D/H (F5 para placar, help para ajuda, Q para sair):%n",
                                turnFlag);
                        break;
                    case 'R':
                        renderSnapshot(line);
                        state.setAwaitingAction(false);
                        break;
                    case 'E':
                        handleEndMessage(line);
                        state.stop();
                        break;
                    case 'B':
                        System.out.println("Placar global: " + line.substring(1));
                        break;
                    case 'C':
                        handleClassAnnouncement(line);
                        break;
                    case 'O':
                        // pong
                        break;
                    case 'X':
                        System.out.println("Erro do servidor: " + line.substring(2));
                        state.setAwaitingAction(true);
                        break;
                    default:
                        System.out.println("Mensagem: " + line);
                        break;
                }
            }
        } catch (IOException e) {
            System.out.println("Conexão encerrada pelo servidor.");
        } finally {
            state.stop();
        }
    }

    private void processUserInput(String input, PrintWriter serverWriter) {
        if (!state.isRunning()) {
            return;
        }
        if (input.isEmpty()) {
            return;
        }
        String normalized = input.trim().toUpperCase();
        if ("HELP".equals(normalized)) {
            showHelp();
            return;
        }
        if ("F5".equals(normalized) || "G".equals(normalized)) {
            serverWriter.println("G");
            return;
        }
        if ("Q".equals(normalized)) {
            if (state.getPlayerId() > 0) {
                serverWriter.println("Q" + state.getPlayerId());
            }
            state.stop();
            return;
        }
        if (state.isAwaitingClass()) {
            handleClassSelection(normalized, serverWriter);
        } else if (state.isAwaitingAction()) {
            handleActionSelection(normalized, serverWriter);
        } else {
            System.out.println("Aguardando o próximo evento do servidor...");
        }
    }

    private void handleClassSelection(String input, PrintWriter serverWriter) {
        if (input.length() != 1) {
            System.out.println("Entrada inválida. Use G, M ou R.");
            return;
        }
        PlayerClass classe = PlayerClass.fromCode(input.charAt(0));
        if (classe == PlayerClass.UNKNOWN) {
            System.out.println("Classe inválida. Escolha G, M ou R.");
            return;
        }
        serverWriter.println("J" + state.getPlayerId() + classe.getCode());
        state.setPlayerClass(classe);
        state.setAwaitingClass(false);
        System.out.println("Classe " + classe + " enviada. Aguarde o turno.");
    }

    private void handleActionSelection(String input, PrintWriter serverWriter) {
        if (input.length() != 1) {
            System.out.println("Ação inválida. Use A, S, D ou H.");
            return;
        }
        ActionType action = ActionType.fromCode(input.charAt(0));
        if (action == ActionType.INVALID) {
            System.out.println("Ação inválida. Use A, S, D ou H.");
            return;
        }
        if (action == ActionType.HEAL && !state.getPlayerClass().hasHeal()) {
            System.out.println("Sua classe não possui habilidade de cura.");
            return;
        }
        String message;
        if (action == ActionType.SKILL || action == ActionType.HEAL) {
            message = String.format("M%d%d%c0", state.getCurrentTurnFlag(), state.getPlayerId(), action.getCode());
        } else {
            message = String.format("M%d%d%c", state.getCurrentTurnFlag(), state.getPlayerId(), action.getCode());
        }
        serverWriter.println(message);
        state.setAwaitingAction(false);
        System.out.println("Ação enviada: " + action.name());
    }

    private void renderSnapshot(String message) {
        // Mensagem no formato R<vez>|H1:HP/MP|H2:HP/MP|C1:cdS/cdH|C2:cdS/cdH|L:log
        String[] parts = message.split("\\|");
        if (parts.length < 6) {
            System.out.println("Snapshot inválido: " + message);
            return;
        }
        System.out.println("\n=== Duel RPG Online ===");
        for (int i = 1; i <= 4; i++) {
            System.out.println(parts[i]);
        }
        String log = parts[5].length() > 2 ? parts[5].substring(2) : "-";
        System.out.println("Resumo: " + log);
    }

    private void handleEndMessage(String message) {
        int winner = Character.digit(message.charAt(1), 10);
        String reason = message.contains("reason:") ? message.substring(message.indexOf("reason:") + 7) : "";
        System.out.println("\n=== Fim de jogo ===");
        if (winner == 0) {
            System.out.println("Empate! Motivo: " + reason);
        } else if (winner == state.getPlayerId()) {
            System.out.println("Vitória! Motivo: " + reason);
        } else {
            System.out.println("Derrota. Motivo: " + reason);
        }
    }

    private void handleClassAnnouncement(String message) {
        if (message.length() < 3) {
            return;
        }
        int playerId = Character.digit(message.charAt(1), 10);
        PlayerClass clazz = PlayerClass.fromCode(message.charAt(2));
        if (clazz == PlayerClass.UNKNOWN) {
            return;
        }
        System.out.printf("Jogador %d escolheu %s.%n", playerId, clazz);
        if (playerId == state.getPlayerId()) {
            state.setPlayerClass(clazz);
            state.setAwaitingClass(false);
        }
    }

    private void showHelp() {
        System.out.println("\nComandos disponíveis:");
        System.out.println("  A - Ataque básico");
        System.out.println("  S - Skill da classe");
        System.out.println("  D - Defesa");
        System.out.println("  H - Cura (apenas Mago)");
        System.out.println("  F5 ou G - Placar global");
        System.out.println("  help - Exibe esta ajuda");
        System.out.println("  Q - Sair");
    }
}

