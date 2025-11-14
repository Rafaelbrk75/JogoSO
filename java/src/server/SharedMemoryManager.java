package server;

import common.PlayerClass;
import common.SharedState;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class SharedMemoryManager implements AutoCloseable {

    private static final String FILE_NAME = "DUELRPG_MM.dat";
    private static final int BUFFER_SIZE = 512;

    private final RandomAccessFile raf;
    private final FileChannel channel;
    private final MappedByteBuffer buffer;

    public SharedMemoryManager() throws IOException {
        File file = new File(FILE_NAME);
        if (!file.exists()) {
            try (RandomAccessFile init = new RandomAccessFile(file, "rw")) {
                init.setLength(BUFFER_SIZE);
            }
        }

        this.raf = new RandomAccessFile(file, "rw");
        this.channel = raf.getChannel();
        if (raf.length() < BUFFER_SIZE) {
            raf.setLength(BUFFER_SIZE);
        }
        this.buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, BUFFER_SIZE);
    }

    public synchronized void reset() {
        buffer.position(0);
        byte[] zero = new byte[BUFFER_SIZE];
        buffer.put(zero);
        buffer.force();
    }

    public synchronized SharedState readState() {
        buffer.position(0);
        SharedState state = new SharedState();
        state.setPartidasAtivas(buffer.getInt());
        for (int i = 0; i < 3; i++) {
            state.setVitoriaCount(i, buffer.getInt());
        }
        int length = buffer.getInt();
        length = Math.max(0, Math.min(length, BUFFER_SIZE - buffer.position()));
        byte[] data = new byte[length];
        buffer.get(data);
        state.setUltimoLog(new String(data, StandardCharsets.UTF_8));
        return state;
    }

    public synchronized void writeState(SharedState state) {
        buffer.position(0);
        buffer.putInt(state.getPartidasAtivas());
        Arrays.stream(state.getVitoriasClasse()).forEach(buffer::putInt);
        byte[] data = state.getUltimoLog().getBytes(StandardCharsets.UTF_8);
        int len = Math.min(data.length, BUFFER_SIZE - buffer.position() - 4);
        buffer.putInt(len);
        buffer.put(data, 0, len);
        buffer.force();
    }

    public synchronized void updateTurnLog(String logLine) {
        SharedState state = readState();
        state.setPartidasAtivas(1);
        state.setUltimoLog(logLine == null ? "" : logLine);
        writeState(state);
    }

    public synchronized void registerResult(PlayerClass winner) {
        SharedState state = readState();
        state.setPartidasAtivas(0);
        if (winner != PlayerClass.UNKNOWN) {
            state.incrementVictory(winner);
        }
        writeState(state);
    }

    @Override
    public void close() throws IOException {
        channel.close();
        raf.close();
    }
}

