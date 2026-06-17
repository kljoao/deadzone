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
    private long balance; // scraps (moeda) — NÃO é zerado na morte
    private long bounty;  // recompensa pela cabeça do jogador (scraps) — persiste, zera ao ser morto
    private final Set<String> unlockedSkills;

    // recompensa diária (login streak) — persistido, não zera na morte
    private long dailyStreak;     // dias consecutivos resgatados (cresce; a recompensa cicla)
    private long lastDailyClaim;  // epoch ms do último resgate (a data é derivada disso)

    // estatísticas vitalícias — NÃO zeram na morte
    private long zombiesKilled;
    private long playersKilled;
    private long deaths;
    private long revives;         // reanimações concluídas (como reanimador)
    private long bestSurvivalMs;  // maior tempo vivido numa única vida
    private long lifeStartedAt;   // início da vida atual (reinicia na morte) — base do "tempo sobrevivido"

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
        p.lifeStartedAt = now;
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
                balance,
                bounty,
                new HashSet<>(unlockedSkills),
                dailyStreak,
                lastDailyClaim,
                zombiesKilled,
                playersKilled,
                deaths,
                revives,
                bestSurvivalMs,
                lifeStartedAt
        );
    }

    // ----- recompensa diária -----

    public long getDailyStreak() {
        return dailyStreak;
    }

    public void setDailyStreak(long dailyStreak) {
        this.dailyStreak = dailyStreak;
        this.dirty = true;
    }

    public long getLastDailyClaim() {
        return lastDailyClaim;
    }

    public void setLastDailyClaim(long lastDailyClaim) {
        this.lastDailyClaim = lastDailyClaim;
        this.dirty = true;
    }

    // ----- estatísticas (vitalícias) -----

    public long getZombiesKilled() {
        return zombiesKilled;
    }

    public void setZombiesKilled(long v) {
        this.zombiesKilled = v;
    }

    public void addZombieKill() {
        this.zombiesKilled++;
        this.dirty = true;
    }

    public long getPlayersKilled() {
        return playersKilled;
    }

    public void setPlayersKilled(long v) {
        this.playersKilled = v;
    }

    public void addPlayerKill() {
        this.playersKilled++;
        this.dirty = true;
    }

    public long getDeaths() {
        return deaths;
    }

    public void setDeaths(long v) {
        this.deaths = v;
    }

    public void addDeath() {
        this.deaths++;
        this.dirty = true;
    }

    public long getRevives() {
        return revives;
    }

    public void setRevives(long v) {
        this.revives = v;
    }

    public void addRevive() {
        this.revives++;
        this.dirty = true;
    }

    public long getBestSurvivalMs() {
        return bestSurvivalMs;
    }

    public void setBestSurvivalMs(long v) {
        this.bestSurvivalMs = v;
    }

    public long getLifeStartedAt() {
        return lifeStartedAt;
    }

    public void setLifeStartedAt(long lifeStartedAt) {
        this.lifeStartedAt = lifeStartedAt;
        this.dirty = true;
    }

    /** Fim de uma vida: atualiza o recorde de sobrevivência e reinicia o cronômetro da vida. */
    public void recordDeathSurvival(long now) {
        if (lifeStartedAt > 0 && now > lifeStartedAt) {
            long span = now - lifeStartedAt;
            if (span > bestSurvivalMs) {
                this.bestSurvivalMs = span;
            }
        }
        this.lifeStartedAt = now;
        this.dirty = true;
    }

    // ----- scraps (moeda) -----

    public long getBalance() {
        return balance;
    }

    /** Define o saldo (nunca negativo). */
    public void setBalance(long balance) {
        this.balance = Math.max(0L, balance);
        this.dirty = true;
    }

    /** Soma (ou subtrai, se delta < 0) ao saldo, com piso em 0. */
    public void addBalance(long delta) {
        setBalance(this.balance + delta);
    }

    // ----- bounty (recompensa pela cabeça) -----

    public long getBounty() {
        return bounty;
    }

    public void setBounty(long bounty) {
        this.bounty = Math.max(0L, bounty);
        this.dirty = true;
    }

    public void addBounty(long delta) {
        setBounty(this.bounty + delta);
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
