package common;

import java.util.Optional;

public final class Protocol {

    private Protocol() {
    }

    public static final int SERVER_PORT = 5050;
    private static final int MAX_MESSAGE_SIZE = 256;

    public static Optional<JoinMessage> parseJoin(String message) {
        if (message == null || !message.startsWith("J") || message.length() < 3) {
            return Optional.empty();
        }
        int playerId = Character.digit(message.charAt(1), 10);
        if (playerId <= 0) {
            return Optional.empty();
        }
        PlayerClass classe = PlayerClass.fromCode(message.charAt(2));
        if (classe == PlayerClass.UNKNOWN) {
            return Optional.empty();
        }
        return Optional.of(new JoinMessage(playerId, classe));
    }

    public static Optional<ActionMessage> parseAction(String message) {
        if (message == null || !message.startsWith("M") || message.length() < 4) {
            return Optional.empty();
        }

        int turnFlag = Character.digit(message.charAt(1), 10);
        int playerId = Character.digit(message.charAt(2), 10);
        if (turnFlag < 0 || playerId <= 0) {
            return Optional.empty();
        }

        ActionType action = ActionType.fromCode(message.charAt(3));
        if (action == ActionType.INVALID) {
            return Optional.empty();
        }

        int skillIndex = 0;
        if ((action == ActionType.SKILL || action == ActionType.HEAL)) {
            if (message.length() < 5) {
                return Optional.empty();
            }
            skillIndex = Character.digit(message.charAt(4), 10);
            if (skillIndex < 0) {
                return Optional.empty();
            }
        }

        return Optional.of(new ActionMessage(turnFlag, playerId, action, skillIndex));
    }

    public static boolean isQuit(String message) {
        return message != null && message.startsWith("Q");
    }

    public static boolean isPing(String message) {
        return message != null && message.startsWith("P");
    }

    public static boolean isScoreboardRequest(String message) {
        return message != null && message.startsWith("G");
    }

    public static String formatTurnSnapshot(int turnFlag,
                                            int hp1, int mp1, int cdS1, int cdH1,
                                            int hp2, int mp2, int cdS2, int cdH2,
                                            String logLine) {
        String log = (logLine == null || logLine.isEmpty()) ? "-" : logLine;
        return String.format("R%d|H1:%d/%d|H2:%d/%d|C1:%d/%d|C2:%d/%d|L:%s",
                turnFlag, hp1, mp1, hp2, mp2, cdS1, cdH1, cdS2, cdH2, log);
    }

    public static int getMaxMessageSize() {
        return MAX_MESSAGE_SIZE;
    }

    public static final class JoinMessage {
        private final int playerId;
        private final PlayerClass playerClass;

        public JoinMessage(int playerId, PlayerClass playerClass) {
            this.playerId = playerId;
            this.playerClass = playerClass;
        }

        public int getPlayerId() {
            return playerId;
        }

        public PlayerClass getPlayerClass() {
            return playerClass;
        }
    }

    public static final class ActionMessage {
        private final int turnFlag;
        private final int playerId;
        private final ActionType action;
        private final int skillIndex;

        public ActionMessage(int turnFlag, int playerId, ActionType action, int skillIndex) {
            this.turnFlag = turnFlag;
            this.playerId = playerId;
            this.action = action;
            this.skillIndex = skillIndex;
        }

        public int getTurnFlag() {
            return turnFlag;
        }

        public int getPlayerId() {
            return playerId;
        }

        public ActionType getAction() {
            return action;
        }

        public int getSkillIndex() {
            return skillIndex;
        }
    }
}

