package server;

import common.PlayerClass;

public class PlayerState {
    private final PlayerClass playerClass;
    private int hp = 100;
    private int mp = 50;
    private int skillCooldown = 0;
    private int healCooldown = 0;

    public PlayerState(PlayerClass playerClass) {
        this.playerClass = playerClass;
    }

    public PlayerClass getPlayerClass() {
        return playerClass;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = Math.max(0, Math.min(100, hp));
    }

    public int getMp() {
        return mp;
    }

    public void setMp(int mp) {
        this.mp = Math.max(0, Math.min(50, mp));
    }

    public int getSkillCooldown() {
        return skillCooldown;
    }

    public void setSkillCooldown(int skillCooldown) {
        this.skillCooldown = Math.max(0, skillCooldown);
    }

    public int getHealCooldown() {
        return healCooldown;
    }

    public void setHealCooldown(int healCooldown) {
        this.healCooldown = Math.max(0, healCooldown);
    }

    public boolean isAlive() {
        return hp > 0;
    }

    public void decrementCooldowns() {
        if (skillCooldown > 0) {
            skillCooldown--;
        }
        if (healCooldown > 0) {
            healCooldown--;
        }
    }
}

