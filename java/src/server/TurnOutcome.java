package server;

import common.ActionType;

public class TurnOutcome {
    private final int[] damageTaken = new int[2];
    private final int[] healed = new int[2];
    private final boolean[] fallback = new boolean[2];
    private final boolean[] autoAssigned = new boolean[2];
    private final ActionType[] executedActions = new ActionType[]{ActionType.DEFEND, ActionType.DEFEND};
    private String logLine = "-";
    private int winnerId = 0;
    private boolean tie = false;
    private String reason = "";

    public int[] getDamageTaken() {
        return damageTaken;
    }

    public int[] getHealed() {
        return healed;
    }

    public boolean[] getFallback() {
        return fallback;
    }

    public boolean[] getAutoAssigned() {
        return autoAssigned;
    }

    public ActionType[] getExecutedActions() {
        return executedActions;
    }

    public String getLogLine() {
        return logLine;
    }

    public void setLogLine(String logLine) {
        this.logLine = logLine;
    }

    public int getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(int winnerId) {
        this.winnerId = winnerId;
    }

    public boolean isTie() {
        return tie;
    }

    public void setTie(boolean tie) {
        this.tie = tie;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

