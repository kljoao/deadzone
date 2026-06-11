package com.deadzone.core.profile;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Estado por jogador, carregado no join e salvo de forma assíncrona.
 * Campos persistidos: infecção, sanidade, classe, XP e skills.
 * Campos transitórios (bleed/downed) vivem só em memória.
 */
public class PlayerProfile {

    private final UUID uuid;
    private String lastKnownName;

    private boolean infected;
    private double infectionLevel; // 0..100

    private double sanity; // 0..100, alto = saudável

    private PlayerClass playerClass;
    private long xp;
    private long totalXpEarned;
    private final Set<String> unlockedSkills;

    private long firstJoin;
    private long lastSeen;

    // estado transitório; downedState persiste só o expiresAt (downed_until) p/ sobreviver ao relog
    private transient DownedState downedState;
    private transient BleedState bleedState;
    private transient long lastDamageByZombie;
    private transient boolean dyingFromInfection;

    private transient boolean dirty;

    public PlayerProfile(UUID uuid) {
        this.uuid = uuid;
        this.unlockedSkills = new HashSet<>();
        this.playerClass = PlayerClass.NONE;
        this.sanity = 100.0;
    }

    public static PlayerProfile createDefault(UUID uuid, String name) {
        PlayerProfile p = new PlayerProfile(uuid);
        p.lastKnownName = name;
        long now = System.currentTimeMillis();
        p.firstJoin = now;
        p.lastSeen = now;
        p.dirty = true;
        return p;
    }

    /** Reset total na morte. Itens são tratados pelo vanilla (drop/limpeza). */
    public void resetToDefaults(boolean resetXp, boolean keepTotalXpStat) {
        this.infected = false;
        this.infectionLevel = 0.0;
        this.sanity = 100.0;
        this.playerClass = PlayerClass.NONE;
        this.unlockedSkills.clear();
        if (resetXp) {
            this.xp = 0;
        }
        if (!keepTotalXpStat) {
            this.totalXpEarned = 0;
        }
        this.bleedState = null;
        this.downedState = null;
        this.dyingFromInfection = false;
        this.dirty = true;
    }

    /** Cópia imutável para serialização assíncrona segura. */
    public ProfileSnapshot snapshot() {
        return new ProfileSnapshot(
                uuid,
                lastKnownName,
                infected,
                infectionLevel,
                sanity,
                playerClass,
                xp,
                totalXpEarned,
                firstJoin,
                lastSeen,
                downedState != null ? downedState.getExpiresAt() : 0L,
                new HashSet<>(unlockedSkills)
        );
    }

    // setters de campos persistidos marcam dirty

    public UUID getUuid() {
        return uuid;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
        this.dirty = true;
    }

    public boolean isInfected() {
        return infected;
    }

    public void setInfected(boolean infected) {
        this.infected = infected;
        this.dirty = true;
    }

    public double getInfectionLevel() {
        return infectionLevel;
    }

    public void setInfectionLevel(double infectionLevel) {
        this.infectionLevel = Math.max(0.0, Math.min(100.0, infectionLevel));
        this.dirty = true;
    }

    public double getSanity() {
        return sanity;
    }

    public void setSanity(double sanity) {
        this.sanity = Math.max(0.0, Math.min(100.0, sanity));
        this.dirty = true;
    }

    public PlayerClass getPlayerClass() {
        return playerClass;
    }

    public void setPlayerClass(PlayerClass playerClass) {
        this.playerClass = playerClass;
        this.dirty = true;
    }

    public long getXp() {
        return xp;
    }

    public void setXp(long xp) {
        this.xp = xp;
        this.dirty = true;
    }

    public void addXp(long amount) {
        if (amount <= 0) {
            return;
        }
        this.xp += amount;
        this.totalXpEarned += amount;
        this.dirty = true;
    }

    public long getTotalXpEarned() {
        return totalXpEarned;
    }

    /** Usado pela camada de persistência ao carregar; não marca dirty. */
    public void setTotalXpEarned(long totalXpEarned) {
        this.totalXpEarned = totalXpEarned;
    }

    public Set<String> getUnlockedSkills() {
        return unlockedSkills;
    }

    public boolean hasSkill(String skillId) {
        return unlockedSkills.contains(skillId);
    }

    public void unlockSkill(String skillId) {
        if (unlockedSkills.add(skillId)) {
            this.dirty = true;
        }
    }

    public void lockSkill(String skillId) {
        if (unlockedSkills.remove(skillId)) {
            this.dirty = true;
        }
    }

    public long getFirstJoin() {
        return firstJoin;
    }

    public void setFirstJoin(long firstJoin) {
        this.firstJoin = firstJoin;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
        this.dirty = true;
    }

    public DownedState getDownedState() {
        return downedState;
    }

    public void setDownedState(DownedState downedState) {
        this.downedState = downedState;
        this.dirty = true; // persistido (downed_until) p/ sobreviver ao relog
    }

    public BleedState getBleedState() {
        return bleedState;
    }

    public void setBleedState(BleedState bleedState) {
        this.bleedState = bleedState;
    }

    public long getLastDamageByZombie() {
        return lastDamageByZombie;
    }

    public void markLastDamageByZombie() {
        this.lastDamageByZombie = System.currentTimeMillis();
    }

    public boolean isDyingFromInfection() {
        return dyingFromInfection;
    }

    public void setDyingFromInfection(boolean dyingFromInfection) {
        this.dyingFromInfection = dyingFromInfection;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}
