package clientfx;

import client.ClientState;
import common.ActionType;
import common.PlayerClass;
import common.Protocol;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class DuelClientFX extends Application {

    private final ClientState state = new ClientState();

    private Label statusLabel;
    private TextArea logArea;
    private FxPlayerCard[] playerCards;
    private Button[] classButtons;
    private Button attackBtn;
    private Button skillBtn;
    private Button defendBtn;
    private Button healBtn;
    private Button scoreboardBtn;
    private Button helpBtn;
    private Button quitBtn;

    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private Thread listenerThread;

    private String host;
    private int port;

    @Override
    public void start(Stage stage) {
        Parameters params = getParameters();
        this.host = params.getRaw().isEmpty() ? "127.0.0.1" : params.getRaw().get(0);
        this.port = Protocol.SERVER_PORT;

        BorderPane root = new BorderPane();

        statusLabel = new Label("Conectando...");
        statusLabel.getStyleClass().add("status-bar");
        root.setTop(statusLabel);

        playerCards = new FxPlayerCard[]{
                new FxPlayerCard("Jogador 1"),
                new FxPlayerCard("Jogador 2")
        };

        HBox playersBox = new HBox(24, playerCards[0], playerCards[1]);
        playersBox.getStyleClass().add("player-container");
        playersBox.setAlignment(Pos.CENTER);
        playersBox.setPadding(new Insets(20, 24, 20, 24));
        HBox.setHgrow(playerCards[0], Priority.ALWAYS);
        HBox.setHgrow(playerCards[1], Priority.ALWAYS);
        root.setCenter(playersBox);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefWidth(340);
        logArea.getStyleClass().add("log-area");
        root.setRight(logArea);

        VBox bottom = new VBox(12);
        bottom.getStyleClass().add("control-panel");
        bottom.setPadding(new Insets(14, 18, 18, 18));

        classButtons = new Button[]{
                createPrimaryButton("Guerreiro", () -> sendClass(PlayerClass.GUERREIRO)),
                createPrimaryButton("Mago", () -> sendClass(PlayerClass.MAGO)),
                createPrimaryButton("Arqueiro", () -> sendClass(PlayerClass.ARQUEIRO))
        };
        HBox classRow = new HBox(12, classButtons[0], classButtons[1], classButtons[2]);
        classRow.setAlignment(Pos.CENTER);
        classRow.getStyleClass().add("button-row");

        attackBtn = createActionButton("Atacar (A)", () -> sendAction(ActionType.ATTACK));
        skillBtn = createActionButton("Skill (S)", () -> sendAction(ActionType.SKILL));
        defendBtn = createActionButton("Defender (D)", () -> sendAction(ActionType.DEFEND));
        healBtn = createActionButton("Curar (H)", () -> sendAction(ActionType.HEAL));
        HBox actionRow = new HBox(12, attackBtn, skillBtn, defendBtn, healBtn);
        actionRow.setAlignment(Pos.CENTER);
        actionRow.getStyleClass().add("button-row");

        scoreboardBtn = createSecondaryButton("Placar", this::requestScoreboard);
        helpBtn = createSecondaryButton("Ajuda", this::showHelpDialog);
        quitBtn = createDangerButton("Sair", () -> {
            sendQuit();
            closeConnections();
            Platform.exit();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox miscRow = new HBox(12, scoreboardBtn, helpBtn, spacer, quitBtn);
        miscRow.setAlignment(Pos.CENTER_LEFT);
        miscRow.getStyleClass().add("button-row");

        bottom.getChildren().addAll(classRow, actionRow, miscRow);
        root.setBottom(bottom);

        Scene scene = new Scene(root, 1220, 720);
        scene.getStylesheets().add(Optional.ofNullable(
                DuelClientFX.class.getResource("/styles/app.css"))
                .map(URL -> URL.toExternalForm())
                .orElse(""));

        stage.setTitle("Duel RPG Online - Cliente JavaFX");
        stage.setScene(scene);
        stage.setMinWidth(1080);
        stage.setMinHeight(680);
        stage.show();

        stage.setOnCloseRequest(evt -> {
            sendQuit();
            closeConnections();
            Platform.exit();
        });

        connect();
        refreshControls();
    }

    private Button createPrimaryButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll("control-button", "primary");
        button.setOnAction(e -> action.run());
        return button;
    }

    private Button createSecondaryButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll("control-button", "secondary");
        button.setOnAction(e -> action.run());
        return button;
    }

    private Button createDangerButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll("control-button", "danger");
        button.setOnAction(e -> action.run());
        return button;
    }

    private Button createActionButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll("control-button", "action");
        button.setOnAction(e -> action.run());
        return button;
    }

    private void connect() {
        appendLog("Conectando ao servidor " + host + ":" + port + "...");
        listenerThread = new Thread(() -> {
            try {
                socket = new Socket(host, port);
                writer = new PrintWriter(socket.getOutputStream(), true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                Platform.runLater(() -> {
                    statusLabel.setText("Conectado a " + host + ":" + port);
                    appendLog("Conectado! Aguardando servidor.");
                    state.setConnected(true);
                    refreshControls();
                });

                String line;
                while ((line = reader.readLine()) != null) {
                    String message = line.trim();
                    Platform.runLater(() -> handleServerMessage(message));
                }
            } catch (IOException e) {
                Platform.runLater(() -> appendLog("Conexao encerrada: " + e.getMessage()));
            } finally {
                Platform.runLater(() -> {
                    state.stop();
                    state.setConnected(false);
                    refreshControls();
                });
                closeConnections();
            }
        }, "server-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void refreshControls() {
        boolean awaitingClass = state.isAwaitingClass();
        boolean awaitingAction = state.isAwaitingAction();
        boolean connected = state.isConnected();

        for (Button btn : classButtons) {
            btn.setDisable(!(connected && awaitingClass));
        }

        attackBtn.setDisable(!awaitingAction);
        skillBtn.setDisable(!awaitingAction);
        defendBtn.setDisable(!awaitingAction);
        healBtn.setDisable(!(awaitingAction && state.getPlayerClass().hasHeal()));

        scoreboardBtn.setDisable(!connected);
        helpBtn.setDisable(false);
        quitBtn.setDisable(!connected);
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
                appendLog(String.format("Bem-vindo, jogador %d. Aguarde oponente.", id));
                updatePlayerOwnership();
                break;
            case 'S':
                int target = Character.digit(line.charAt(1), 10);
                if (target == state.getPlayerId()) {
                    state.setAwaitingClass(true);
                    appendLog("Selecione sua classe usando os botões.");
                    refreshControls();
                }
                break;
            case 'C':
                handleClassAnnouncement(line);
                break;
            case 'T':
                int turnFlag = Character.digit(line.charAt(1), 10);
                state.setCurrentTurnFlag(turnFlag);
                state.setAwaitingAction(true);
                appendLog("Turno sinalizado (" + turnFlag + "). Use A/S/D/H.");
                updateTurnHighlight();
                refreshControls();
                break;
            case 'R':
                renderSnapshot(line);
                state.setAwaitingAction(false);
                updateTurnHighlight();
                refreshControls();
                break;
            case 'E':
                handleEnd(line);
                state.setAwaitingAction(false);
                state.stop();
                updateTurnHighlight();
                refreshControls();
                break;
            case 'B':
                showScoreboard(line.substring(1));
                break;
            case 'X':
                appendLog("Erro do servidor: " + line.substring(2));
                refreshControls();
                break;
            case 'O':
                break;
            default:
                appendLog("Mensagem: " + line);
                break;
        }
    }

    private void renderSnapshot(String message) {
        String[] parts = message.split("\\|");
        if (parts.length < 6) {
            appendLog("Snapshot inválido: " + message);
            return;
        }
        int[] hpMp1 = parsePair(parts[1]);
        int[] hpMp2 = parsePair(parts[2]);

        playerCards[0].updateStats(hpMp1[0], hpMp1[1]);
        playerCards[1].updateStats(hpMp2[0], hpMp2[1]);

        String log = parts[5].length() > 2 ? parts[5].substring(2) : "-";
        appendLog(String.format("Turno %d → %s", state.getCurrentTurnFlag(), log));
    }

    private void handleEnd(String message) {
        int winner = Character.digit(message.charAt(1), 10);
        String reason = message.contains("reason:") ? message.substring(message.indexOf("reason:") + 7) : "";
        appendLog("=== Fim de jogo ===");
        if (winner == 0) {
            appendLog("Empate! Motivo: " + reason);
        } else if (winner == state.getPlayerId()) {
            appendLog("Vitória! Motivo: " + reason);
        } else {
            appendLog("Derrota. Motivo: " + reason);
        }
        showInfoAlert("Fim da partida",
                winner == 0 ? "Empate!" : (winner == state.getPlayerId() ? "Vitória!" : "Derrota."),
                reason.isEmpty() ? "Partida encerrada." : reason);
    }

    private void handleClassAnnouncement(String message) {
        if (message.length() < 3) {
            return;
        }
        int playerId = Character.digit(message.charAt(1), 10);
        PlayerClass clazz = PlayerClass.fromCode(message.charAt(2));
        if (clazz == PlayerClass.UNKNOWN || playerId < 1 || playerId > playerCards.length) {
            return;
        }
        appendLog(String.format("Jogador %d selecionou %s.", playerId, clazz));
        playerCards[playerId - 1].updateClass(clazz);
        if (playerId == state.getPlayerId()) {
            state.setPlayerClass(clazz);
            state.setAwaitingClass(false);
            refreshControls();
        }
    }

    private void updatePlayerOwnership() {
        for (int i = 0; i < playerCards.length; i++) {
            boolean you = (state.getPlayerId() == (i + 1));
            playerCards[i].setPlayerName("Jogador " + (i + 1) + (you ? " (Você)" : ""));
            playerCards[i].setTurnActive(false);
        }
    }

    private void updateTurnHighlight() {
        for (int i = 0; i < playerCards.length; i++) {
            boolean you = (state.getPlayerId() == (i + 1));
            playerCards[i].setTurnActive(you && state.isAwaitingAction());
        }
    }

    private void sendClass(PlayerClass clazz) {
        if (!state.isAwaitingClass() || writer == null) {
            return;
        }
        writer.println("J" + state.getPlayerId() + clazz.getCode());
        writer.flush();
    }

    private void sendAction(ActionType action) {
        if (!state.isAwaitingAction() || writer == null) {
            return;
        }
        if (action == ActionType.HEAL && !state.getPlayerClass().hasHeal()) {
            appendLog("Sua classe não possui habilidade de cura.");
            return;
        }
        String message;
        if (action == ActionType.SKILL || action == ActionType.HEAL) {
            message = String.format("M%d%d%c0", state.getCurrentTurnFlag(), state.getPlayerId(), action.getCode());
        } else {
            message = String.format("M%d%d%c", state.getCurrentTurnFlag(), state.getPlayerId(), action.getCode());
        }
        writer.println(message);
        writer.flush();
        state.setAwaitingAction(false);
        refreshControls();
    }

    private void requestScoreboard() {
        if (writer != null) {
            writer.println("G");
            writer.flush();
        }
    }

    private void showScoreboard(String json) {
        appendLog("Placar global: " + json);
        String formatted = formatScoreboard(json);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Placar Global");
        alert.setHeaderText("Resumo das vitórias por classe");
        alert.setContentText(formatted);
        alert.showAndWait();
    }

    private void showHelpDialog() {
        showInfoAlert("Ajuda",
                "Comandos disponíveis",
                "- Escolha a classe quando solicitado.\n" +
                        "- Durante o turno, use Atacar/Skill/Defender/Curar.\n" +
                        "- Skill e Cura consomem MP e têm cooldown de 2 turnos.\n" +
                        "- O placar mostra vitórias acumuladas e último log.\n" +
                        "- O botão Sair encerra sua sessão atual.");
    }

    private void showInfoAlert(String title, String header, String body) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(body);
        alert.showAndWait();
    }

    private void sendQuit() {
        if (writer != null && state.getPlayerId() > 0) {
            writer.println("Q" + state.getPlayerId());
            writer.flush();
        }
    }

    private void closeConnections() {
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

    private void appendLog(String text) {
        logArea.appendText(text + System.lineSeparator());
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

    private String formatScoreboard(String json) {
        int g = extractInt(json, "\"G\":");
        int m = extractInt(json, "\"M\":");
        int r = extractInt(json, "\"R\":");
        int ativa = extractInt(json, "\"Ativa\":");
        String ultimo = extractString(json, "\"Ultimo\":\"");

        return String.format("Guerreiro: %d%nMago: %d%nArqueiro: %d%n%nPartida ativa: %s%nÚltimo log: %s",
                g, m, r,
                ativa > 0 ? "Sim" : "Não",
                ultimo.isEmpty() ? "-" : ultimo);
    }

    private int extractInt(String json, String key) {
        int idx = json.indexOf(key);
        if (idx >= 0) {
            int start = idx + key.length();
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
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

    public static void main(String[] args) {
        launch(args);
    }
}

