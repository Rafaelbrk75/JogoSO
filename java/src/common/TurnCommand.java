package common;

public class TurnCommand {
    private ActionType action;
    private int skillIndex;
    private boolean autoAssigned;
    private boolean fallbackToAttack;

    public TurnCommand() {
        this.action = ActionType.DEFEND;
    }

    public TurnCommand(ActionType action, int skillIndex) {
        this.action = action;
        this.skillIndex = skillIndex;
    }

    public ActionType getAction() {
        return action;
    }

    public void setAction(ActionType action) {
        this.action = action;
    }

    public int getSkillIndex() {
        return skillIndex;
    }

    public void setSkillIndex(int skillIndex) {
        this.skillIndex = skillIndex;
    }

    public boolean isAutoAssigned() {
        return autoAssigned;
    }

    public void setAutoAssigned(boolean autoAssigned) {
        this.autoAssigned = autoAssigned;
    }

    public boolean isFallbackToAttack() {
        return fallbackToAttack;
    }

    public void setFallbackToAttack(boolean fallbackToAttack) {
        this.fallbackToAttack = fallbackToAttack;
    }
}

