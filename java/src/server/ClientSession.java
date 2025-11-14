package server;

import common.Protocol;
import common.SharedState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientSession implements Runnable {

    private final Socket socket;
    private final int playerId;
    private final DuelServer server;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean connected = new AtomicBoolean(true);
    private final Thread thread;
    private PrintWriter writer;
    private BufferedReader reader;
    private Instant lastMessage = Instant.now();

    public ClientSession(Socket socket, int playerId, DuelServer server) {
        this.socket = socket;
        this.playerId = playerId;
        this.server = server;
        this.thread = new Thread(this, "ClientSession-" + playerId);
    }

    public void start() {
        thread.start();
    }

    public boolean isConnected() {
        return connected.get();
    }

    public int getPlayerId() {
        return playerId;
    }

    public BlockingQueue<String> getQueue() {
        return queue;
    }

    public Instant getLastMessage() {
        return lastMessage;
    }

    public void sendLine(String message) {
        if (writer != null) {
            writer.println(message);
            writer.flush();
            server.log("TX[%d]: %s", playerId, message);
        }
    }

    @Override
    public void run() {
        try {
            this.writer = new PrintWriter(socket.getOutputStream(), true);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            sendLine("W" + playerId);

            String line;
            while ((line = reader.readLine()) != null) {
                lastMessage = Instant.now();
                server.log("RX[%d]: %s", playerId, line);
                handleLine(line.trim());
            }
        } catch (IOException e) {
            server.log("Cliente %d desconectado: %s", playerId, e.getMessage());
        } finally {
            connected.set(false);
            queue.offer("Q");
            close();
        }
    }

    private void handleLine(String line) {
        if (line.isEmpty()) {
            return;
        }
        if (Protocol.isPing(line)) {
            sendLine("O");
            return;
        }
        if (Protocol.isScoreboardRequest(line)) {
            SharedState state = server.getSharedMemory().readState();
            String json = String.format("{\"G\":%d,\"M\":%d,\"R\":%d,\"Ativa\":%d,\"Ultimo\":\"%s\"}",
                    state.getVitoriasClasse()[0],
                    state.getVitoriasClasse()[1],
                    state.getVitoriasClasse()[2],
                    state.getPartidasAtivas(),
                    state.getUltimoLog().replace("\"", "\\\""));
            sendLine("B" + json);
            return;
        }
        if (line.length() > Protocol.getMaxMessageSize()) {
            sendLine("X99|Mensagem muito longa");
            return;
        }
        queue.offer(line);
    }

    public void close() {
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }
}

