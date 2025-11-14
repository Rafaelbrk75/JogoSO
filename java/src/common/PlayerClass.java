package common;

public enum PlayerClass {
    GUERREIRO('G', 12, 25, 10, 2, 1, 0, 0, 0, 50, false),
    MAGO('M', 8, 30, 15, 2, 1, 18, 12, 2, 40, true),
    ARQUEIRO('R', 10, 8, 10, 2, 2, 0, 0, 0, 30, false),
    UNKNOWN('?', 0, 0, 0, 0, 0, 0, 0, 0, 0, false);

    private final char code;
    private final int baseAttack;
    private final int skillDamage;
    private final int skillCost;
    private final int skillCooldown;
    private final int skillHits;
    private final int healAmount;
    private final int healCost;
    private final int healCooldown;
    private final int defensePercent;
    private final boolean hasHeal;

    PlayerClass(char code,
                int baseAttack,
                int skillDamage,
                int skillCost,
                int skillCooldown,
                int skillHits,
                int healAmount,
                int healCost,
                int healCooldown,
                int defensePercent,
                boolean hasHeal) {
        this.code = Character.toUpperCase(code);
        this.baseAttack = baseAttack;
        this.skillDamage = skillDamage;
        this.skillCost = skillCost;
        this.skillCooldown = skillCooldown;
        this.skillHits = skillHits;
        this.healAmount = healAmount;
        this.healCost = healCost;
        this.healCooldown = healCooldown;
        this.defensePercent = defensePercent;
        this.hasHeal = hasHeal;
    }

    public char getCode() {
        return code;
    }

    public int getBaseAttack() {
        return baseAttack;
    }

    public int getSkillDamage() {
        return skillDamage;
    }

    public int getSkillCost() {
        return skillCost;
    }

    public int getSkillCooldown() {
        return skillCooldown;
    }

    public int getSkillHits() {
        return skillHits;
    }

    public int getHealAmount() {
        return healAmount;
    }

    public int getHealCost() {
        return healCost;
    }

    public int getHealCooldown() {
        return healCooldown;
    }

    public int getDefensePercent() {
        return defensePercent;
    }

    public boolean hasHeal() {
        return hasHeal;
    }

    public static PlayerClass fromCode(char c) {
        char upper = Character.toUpperCase(c);
        for (PlayerClass classe : values()) {
            if (classe.code == upper) {
                return classe;
            }
        }
        return UNKNOWN;
    }

    @Override
    public String toString() {
        switch (this) {
            case GUERREIRO:
                return "Guerreiro";
            case MAGO:
                return "Mago";
            case ARQUEIRO:
                return "Arqueiro";
            default:
                return "Desconhecido";
        }
    }
}

