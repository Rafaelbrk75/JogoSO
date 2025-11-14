package server;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ServerLogger implements AutoCloseable {
    private final PrintWriter writer;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ServerLogger(String fileName) throws IOException {
        this.writer = new PrintWriter(new FileWriter(fileName, true), true);
    }

    public synchronized void log(String format, Object... args) {
        String timestamp = LocalDateTime.now().format(formatter);
        writer.printf("[%s] %s%n", timestamp, String.format(format, args));
    }

    @Override
    public void close() {
        writer.flush();
        writer.close();
    }
}

