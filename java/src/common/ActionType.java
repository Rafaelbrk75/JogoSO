package common;

public enum ActionType {
    ATTACK('A'),
    SKILL('S'),
    DEFEND('D'),
    HEAL('H'),
    INVALID('?');

    private final char code;

    ActionType(char code) {
        this.code = code;
    }

    public char getCode() {
        return code;
    }

    public static ActionType fromCode(char c) {
        char upper = Character.toUpperCase(c);
        for (ActionType type : values()) {
            if (type.code == upper) {
                return type;
            }
        }
        return INVALID;
    }
}

