package client;

import common.PlayerClass;

import java.util.concurrent.atomic.AtomicBoolean;

public class ClientState {
    private final AtomicBoolean running = new AtomicBoolean(true);
    private int playerId = 0;
    private PlayerClass playerClass = PlayerClass.UNKNOWN;
    private volatile boolean awaitingClass = false;
    private volatile boolean awaitingAction = false;
    private volatile int currentTurnFlag = 1;
    private volatile boolean connected = false;

    public boolean isRunning() {
        return running.get();
    }

    public void stop() {
        running.set(false);
    }

    public int getPlayerId() {
        return playerId;
    }

    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public PlayerClass getPlayerClass() {
        return playerClass;
    }

    public void setPlayerClass(PlayerClass playerClass) {
        this.playerClass = playerClass;
    }

    public boolean isAwaitingClass() {
        return awaitingClass;
    }

    public void setAwaitingClass(boolean awaitingClass) {
        this.awaitingClass = awaitingClass;
    }

    public boolean isAwaitingAction() {
        return awaitingAction;
    }

    public void setAwaitingAction(boolean awaitingAction) {
        this.awaitingAction = awaitingAction;
    }

    public int getCurrentTurnFlag() {
        return currentTurnFlag;
    }

    public void setCurrentTurnFlag(int currentTurnFlag) {
        this.currentTurnFlag = currentTurnFlag;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }
}

