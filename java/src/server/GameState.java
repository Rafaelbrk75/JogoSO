package server;

import common.ActionType;
import common.PlayerClass;
import common.TurnCommand;

public class GameState {
    private final PlayerState[] players = new PlayerState[2];
    private int turnNumber = 1;

    public GameState(PlayerClass classP1, PlayerClass classP2) {
        players[0] = new PlayerState(classP1);
        players[1] = new PlayerState(classP2);
    }

    public PlayerState[] getPlayers() {
        return players;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public void incrementTurn() {
        turnNumber++;
    }

    public void startNewTurn() {
        for (PlayerState player : players) {
            player.decrementCooldowns();
        }
    }

    public TurnOutcome applyTurn(TurnCommand cmdP1, TurnCommand cmdP2) {
        TurnOutcome outcome = new TurnOutcome();

        TurnCommand[] commands = {cmdP1, cmdP2};
        PlayerState[] states = getPlayers();

        int[] outgoingDamage = new int[2];
        int[] healing = new int[2];
        int[] defense = new int[2];

        for (int i = 0; i < 2; i++) {
            TurnCommand cmd = commands[i];
            PlayerState ps = states[i];
            PlayerClass pc = ps.getPlayerClass();

            ActionType action = cmd.getAction();
            outcome.getExecutedActions()[i] = action;
            outcome.getFallback()[i] = cmd.isFallbackToAttack();
            outcome.getAutoAssigned()[i] = cmd.isAutoAssigned();

            if (action == ActionType.SKILL) {
                if (ps.getSkillCooldown() > 0 || ps.getMp() < pc.getSkillCost()) {
                    action = ActionType.ATTACK;
                    outcome.getFallback()[i] = true;
                }
            } else if (action == ActionType.HEAL) {
                if (!pc.hasHeal() || ps.getHealCooldown() > 0 || ps.getMp() < pc.getHealCost()) {
                    action = ActionType.ATTACK;
                    outcome.getFallback()[i] = true;
                }
            } else if (action == ActionType.DEFEND || action == ActionType.ATTACK) {
                // ok
            } else {
                action = ActionType.DEFEND;
                outcome.getFallback()[i] = true;
            }

            switch (action) {
                case ATTACK:
                    outgoingDamage[i] = pc.getBaseAttack();
                    break;
                case SKILL:
                    outgoingDamage[i] = pc.getSkillDamage() * Math.max(1, pc.getSkillHits());
                    ps.setMp(ps.getMp() - pc.getSkillCost());
                    ps.setSkillCooldown(pc.getSkillCooldown());
                    break;
                case DEFEND:
                    defense[i] = pc.getDefensePercent();
                    break;
                case HEAL:
                    ps.setMp(ps.getMp() - pc.getHealCost());
                    ps.setHealCooldown(pc.getHealCooldown());
                    healing[i] = pc.getHealAmount();
                    break;
                default:
                    break;
            }

            outcome.getExecutedActions()[i] = action;
        }

        for (int i = 0; i < 2; i++) {
            int target = 1 - i;
            int damage = outgoingDamage[i];
            if (damage > 0) {
                int reduced = damage - (damage * defense[target] / 100);
                reduced = Math.max(0, reduced);
                PlayerState targetState = states[target];
                targetState.setHp(targetState.getHp() - reduced);
                outcome.getDamageTaken()[target] += reduced;
            }
        }

        for (int i = 0; i < 2; i++) {
            if (healing[i] > 0) {
                PlayerState ps = states[i];
                int before = ps.getHp();
                ps.setHp(ps.getHp() + healing[i]);
                int actual = ps.getHp() - before;
                outcome.getHealed()[i] = Math.max(0, actual);
            }
        }

        String label1 = describe(outcome.getExecutedActions()[0], outgoingDamage[0], outcome.getHealed()[0]);
        String label2 = describe(outcome.getExecutedActions()[1], outgoingDamage[1], outcome.getHealed()[1]);

        outcome.setLogLine(String.format("P1 %s, P2 %s, dano final %d e %d",
                label1, label2, outcome.getDamageTaken()[0], outcome.getDamageTaken()[1]));

        boolean alive1 = states[0].isAlive();
        boolean alive2 = states[1].isAlive();
        if (!alive1 && !alive2) {
            outcome.setTie(true);
            outcome.setReason("Ambos caíram");
        } else if (!alive1) {
            outcome.setWinnerId(2);
            outcome.setReason("Jogador 1 caiu");
        } else if (!alive2) {
            outcome.setWinnerId(1);
            outcome.setReason("Jogador 2 caiu");
        }

        incrementTurn();
        return outcome;
    }

    private String describe(ActionType action, int damage, int heal) {
        switch (action) {
            case ATTACK:
                return "A" + damage;
            case SKILL:
                return "S" + damage;
            case DEFEND:
                return "D";
            case HEAL:
                return "H" + heal;
            default:
                return "?";
        }
    }
}

