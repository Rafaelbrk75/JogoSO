package client;

import common.ActionType;
import common.PlayerClass;
import common.Protocol;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class DuelClientGUI {

    private final ClientState state = new ClientState();
    private final JTextArea logArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("Desconectado");
    private final PlayerPanel[] playerPanels = new PlayerPanel[]{
            new PlayerPanel("Jogador 1"),
            new PlayerPanel("Jogador 2")
    };
    private JButton[] classButtons;
    private JButton attackBtn;
    private JButton skillBtn;
    private JButton defendBtn;
    private JButton healBtn;
    private JButton scoreboardBtn;
    private JButton helpBtn;
    private JButton quitBtn;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        SwingUtilities.invokeLater(() -> new DuelClientGUI().start(host, Protocol.SERVER_PORT));
    }

    private void start(String host, int port) {
        JFrame frame = new JFrame("Duel RPG Online - Cliente");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                sendQuit();
                cleanup();
                frame.dispose();
                System.exit(0);
            }
        });

        JPanel boardPanel = new JPanel(new GridLayout(1, 2, 12, 12));
        for (int i = 0; i < playerPanels.length; i++) {
            playerPanels[i].setPlayerName("Jogador " + (i + 1));
            playerPanels[i].setIsYou(false);
            playerPanels[i].reset();
            boardPanel.add(playerPanels[i]);
        }
        boardPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new java.awt.Dimension(360, 480));

        JPanel classPanel = new JPanel(new FlowLayout());
        classPanel.setBorder(BorderFactory.createTitledBorder("Classe"));
        JButton warriorBtn = new JButton("Guerreiro");
        JButton mageBtn = new JButton("Mago");
        JButton archerBtn = new JButton("Arqueiro");
        classPanel.add(warriorBtn);
        classPanel.add(mageBtn);
        classPanel.add(archerBtn);
        classButtons = new JButton[]{warriorBtn, mageBtn, archerBtn};

        JPanel actionPanel = new JPanel(new FlowLayout());
        actionPanel.setBorder(BorderFactory.createTitledBorder("Ações"));
        attackBtn = new JButton("Atacar (A)");
        skillBtn = new JButton("Skill (S)");
        defendBtn = new JButton("Defender (D)");
        healBtn = new JButton("Curar (H)");
        actionPanel.add(attackBtn);
        actionPanel.add(skillBtn);
        actionPanel.add(defendBtn);
        actionPanel.add(healBtn);

        JPanel utilPanel = new JPanel(new FlowLayout());
        scoreboardBtn = new JButton("Placar");
        helpBtn = new JButton("Ajuda");
        quitBtn = new JButton("Sair");
        utilPanel.add(scoreboardBtn);
        utilPanel.add(helpBtn);
        utilPanel.add(quitBtn);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, boardPanel, scrollPane);
        splitPane.setResizeWeight(0.6);

        frame.setLayout(new BorderLayout(8, 8));
        frame.add(statusLabel, BorderLayout.NORTH);
        frame.add(splitPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new GridLayout(3, 1));
        bottomPanel.add(classPanel);
        bottomPanel.add(actionPanel);
        bottomPanel.add(utilPanel);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        warriorBtn.addActionListener(e -> sendClass(PlayerClass.GUERREIRO));
        mageBtn.addActionListener(e -> sendClass(PlayerClass.MAGO));
        archerBtn.addActionListener(e -> sendClass(PlayerClass.ARQUEIRO));

        attackBtn.addActionListener(e -> sendAction(ActionType.ATTACK));
        skillBtn.addActionListener(e -> sendAction(ActionType.SKILL));
        defendBtn.addActionListener(e -> sendAction(ActionType.DEFEND));
        healBtn.addActionListener(e -> sendAction(ActionType.HEAL));

        scoreboardBtn.addActionListener(e -> sendScoreboard());
        helpBtn.addActionListener(e -> showHelpDialog());
        quitBtn.addActionListener(e -> {
            sendQuit();
            cleanup();
            frame.dispose();
            System.exit(0);
        });

        refreshButtons();
        connect(host, port);
    }

    private void connect(String host, int port) {
        append("Conectando ao servidor " + host + ":" + port + "...");
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                socket = new Socket(host, port);
                writer = new PrintWriter(socket.getOutputStream(), true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                statusLabel.setText("Conectado a " + host + ":" + port);
                append("Conectado! Aguardando servidor.");
                state.setConnected(true);
                refreshButtons();
                listenServer();
                return null;
            }

            @Override
            protected void done() {
                // nada
            }
        };
        worker.execute();
    }

    private void listenServer() {
        new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String message = line.trim();
                    SwingUtilities.invokeLater(() -> handleServerMessage(message));
                }
                append("Conexão encerrada pelo servidor.");
            } catch (IOException e) {
                append("Erro na conexão: " + e.getMessage());
            } finally {
                state.stop();
                state.setConnected(false);
                refreshButtons();
                cleanup();
            }
        }, "ServerListener").start();
    }

    private void handleServerMessage(String line) {
        if (line.isEmpty()) {
            return;
        }
        char code = line.charAt(0);
        switch (code) {
            case 'W':
                int id = Character.digit(line.charAt(1), 10);
                state.setPlayerId(id);
                append("Bem-vindo, jogador " + id + ". Aguarde oponente.");
                updatePlayerOwnership();
                break;
            case 'S':
                int target = Character.digit(line.charAt(1), 10);
                if (target == state.getPlayerId()) {
                    state.setAwaitingClass(true);
                    append("Selecione sua classe: use os botões acima.");
                    refreshButtons();
                }
                break;
            case 'C':
                handleClassAnnouncement(line);
                break;
            case 'T':
                int turnFlag = Character.digit(line.charAt(1), 10);
                state.setCurrentTurnFlag(turnFlag);
                state.setAwaitingAction(true);
                append("Turno sinalizado (" + turnFlag + "). Use A/S/D/H.");
                updateTurnHighlight();
                refreshButtons();
                break;
            case 'R':
                renderSnapshot(line);
                state.setAwaitingAction(false);
                updateTurnHighlight();
                refreshButtons();
                break;
            case 'E':
                handleEnd(line);
                state.setAwaitingAction(false);
                state.stop();
                refreshButtons();
                break;
            case 'B':
                showScoreboard(line.substring(1));
                break;
            case 'X':
                append("Erro do servidor: " + line.substring(2));
                refreshButtons();
                break;
            case 'O':
                break;
            default:
                append("Mensagem: " + line);
                break;
        }
    }

    private void sendClass(PlayerClass classe) {
        if (!state.isAwaitingClass()) {
            append("Aguardando sinal para escolher classe.");
            return;
        }
        if (writer == null) {
            append("Sem conexão.");
            return;
        }
        writer.println("J" + state.getPlayerId() + classe.getCode());
        writer.flush();
        state.setPlayerClass(classe);
        state.setAwaitingClass(false);
        append("Classe " + classe + " enviada.");
        playerPanels[state.getPlayerId() - 1].updateClass(classe, classe.hasHeal());
        refreshButtons();
    }

    private void sendAction(ActionType action) {
        if (!state.isAwaitingAction()) {
            append("Não é momento de agir. Aguarde o turno.");
            return;
        }
        if (action == ActionType.HEAL && !state.getPlayerClass().hasHeal()) {
            append("Sua classe não possui habilidade de cura.");
            return;
        }
        if (writer == null) {
            append("Sem conexão.");
            return;
        }
        String msg;
        if (action == ActionType.SKILL || action == ActionType.HEAL) {
            msg = String.format("M%d%d%c0", state.getCurrentTurnFlag(), state.getPlayerId(), action.getCode());
        } else {
            msg = String.format("M%d%d%c", state.getCurrentTurnFlag(), state.getPlayerId(), action.getCode());
        }
        writer.println(msg);
        writer.flush();
        state.setAwaitingAction(false);
        append("Ação enviada: " + action.name());
        refreshButtons();
    }

    private void sendScoreboard() {
        if (writer != null) {
            writer.println("G");
        }
    }

    private void sendQuit() {
        if (writer != null && state.getPlayerId() > 0) {
            writer.println("Q" + state.getPlayerId());
            writer.flush();
        }
    }

    private void showHelpDialog() {
        JOptionPane.showMessageDialog(null,
                "Comandos disponíveis:\n" +
                        " - Escolha a classe quando solicitado.\n" +
                        " - Durante o turno, use Atacar/Skill/Defender/Curar.\n" +
                        " - Skill/Cura consomem MP e respeitam cooldown.\n" +
                        " - O placar mostra vitórias por classe.\n" +
                        " - O botão Sair encerra sua sessão.",
                "Ajuda",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void renderSnapshot(String message) {
        String[] parts = message.split("\\|");
        if (parts.length < 6) {
            append("Snapshot inválido: " + message);
            return;
        }
        int[] hpMp1 = parsePair(parts[1]);
        int[] hpMp2 = parsePair(parts[2]);
        int[] cd1 = parsePair(parts[3]);
        int[] cd2 = parsePair(parts[4]);

        playerPanels[0].updateStats(hpMp1[0], hpMp1[1], cd1[0], cd1[1]);
        playerPanels[1].updateStats(hpMp2[0], hpMp2[1], cd2[0], cd2[1]);

        String log = parts[5].length() > 2 ? parts[5].substring(2) : "-";
        append(String.format("Turno %d → %s", state.getCurrentTurnFlag(), log));
    }

    private void handleEnd(String message) {
        int winner = Character.digit(message.charAt(1), 10);
        String reason = message.contains("reason:") ? message.substring(message.indexOf("reason:") + 7) : "";
        append("=== Fim de jogo ===");
        if (winner == 0) {
            append("Empate! Motivo: " + reason);
        } else if (winner == state.getPlayerId()) {
            append("Vitória! Motivo: " + reason);
        } else {
            append("Derrota. Motivo: " + reason);
        }
    }

    private void append(String text) {
        logArea.append(text + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void refreshButtons() {
        boolean classNeeded = state.isAwaitingClass();
        if (classButtons != null) {
            for (JButton btn : classButtons) {
                btn.setEnabled(classNeeded && state.isConnected());
            }
        }

        boolean actionAllowed = state.isAwaitingAction();
        attackBtn.setEnabled(actionAllowed);
        skillBtn.setEnabled(actionAllowed);
        defendBtn.setEnabled(actionAllowed);
        healBtn.setEnabled(actionAllowed && state.getPlayerClass().hasHeal());

        scoreboardBtn.setEnabled(state.isConnected() && !classNeeded);
        helpBtn.setEnabled(true);
        quitBtn.setEnabled(state.isConnected());
    }

    private void updatePlayerOwnership() {
        for (int i = 0; i < playerPanels.length; i++) {
            boolean you = (state.getPlayerId() == (i + 1));
            playerPanels[i].setIsYou(you);
            if (you && state.getPlayerClass() != PlayerClass.UNKNOWN) {
                playerPanels[i].updateClass(state.getPlayerClass(), state.getPlayerClass().hasHeal());
            }
        }
    }

    private void updateTurnHighlight() {
        for (int i = 0; i < playerPanels.length; i++) {
            boolean you = (state.getPlayerId() == (i + 1));
            playerPanels[i].setTurnActive(you && state.isAwaitingAction());
        }
    }

    private int[] parsePair(String segment) {
        int[] result = new int[]{0, 0};
        int colon = segment.indexOf(':');
        int slash = segment.indexOf('/');
        if (colon > 0 && slash > colon) {
            try {
                result[0] = Integer.parseInt(segment.substring(colon + 1, slash));
                result[1] = Integer.parseInt(segment.substring(slash + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private void showScoreboard(String json) {
        append("Placar global: " + json);
        String formatted = formatScoreboard(json);
        JOptionPane.showMessageDialog(null, formatted, "Placar Global",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private String formatScoreboard(String json) {
        int g = extractInt(json, "\"G\":");
        int m = extractInt(json, "\"M\":");
        int r = extractInt(json, "\"R\":");
        int ativa = extractInt(json, "\"Ativa\":");
        String ultimo = extractString(json, "\"Ultimo\":\"");
        return String.format("Vitórias por classe:%n  Guerreiro: %d%n  Mago: %d%n  Arqueiro: %d%n%n" +
                        "Partida ativa: %s%nÚltimo log: %s",
                g, m, r,
                ativa > 0 ? "Sim" : "Não",
                ultimo.isEmpty() ? "-" : ultimo);
    }

    private int extractInt(String json, String key) {
        int idx = json.indexOf(key);
        if (idx >= 0) {
            int start = idx + key.length();
            int end = start;
            while (end < json.length() && Character.isDigit(json.charAt(end))) {
                end++;
            }
            try {
                return Integer.parseInt(json.substring(start, end));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private String extractString(String json, String key) {
        int idx = json.indexOf(key);
        if (idx >= 0) {
            int start = idx + key.length();
            int end = json.indexOf('"', start);
            if (end > start) {
                return json.substring(start, end).replace("\\\"", "\"");
            }
        }
        return "";
    }

    private void cleanup() {
        try {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void handleClassAnnouncement(String message) {
        if (message.length() < 3) {
            return;
        }
        int playerId = Character.digit(message.charAt(1), 10);
        PlayerClass clazz = PlayerClass.fromCode(message.charAt(2));
        if (clazz == PlayerClass.UNKNOWN || playerId < 1 || playerId > playerPanels.length) {
            return;
        }
        append(String.format("Jogador %d selecionou %s.", playerId, clazz));
        playerPanels[playerId - 1].updateClass(clazz, clazz.hasHeal());
        if (playerId == state.getPlayerId()) {
            state.setPlayerClass(clazz);
            state.setAwaitingClass(false);
            refreshButtons();
        }
    }
}

