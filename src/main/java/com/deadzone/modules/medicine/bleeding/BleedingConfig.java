package com.deadzone.modules.medicine.bleeding;

import com.deadzone.core.config.ConfigManager;

/** Acesso tipado ao bleeding.yml. */
public class BleedingConfig {

    public enum Scaling {INTERVAL, DAMAGE, BOTH}

    private final ConfigManager configManager;

    private double chanceOnHit;
    private int maxSeverity;
    private double damagePerTick;
    private double baseIntervalSeconds;
    private double minIntervalSeconds;
    private Scaling scaling;
    private boolean particles;
    private boolean ambientParticles;
    private boolean statusBossbar;

    public BleedingConfig(ConfigManager configManager) {
        this.configManager = configManager;
        load();
    }

    public void load() {
        var c = configManager.loadConfig("bleeding.yml");
        this.chanceOnHit = c.getDouble("chance-on-zombie-hit", 0.50);
        this.maxSeverity = Math.max(1, c.getInt("max-severity", 6));
        this.damagePerTick = c.getDouble("damage-per-tick", 1.0);
        this.baseIntervalSeconds = Math.max(1, c.getDouble("base-interval-seconds", 15));
        this.minIntervalSeconds = Math.max(1, c.getDouble("min-interval-seconds", 3));
        String scalingName = c.getString("scaling", "INTERVAL");
        try {
            this.scaling = Scaling.valueOf(scalingName.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            this.scaling = Scaling.INTERVAL;
        }
        this.particles = c.getBoolean("particles", true);
        this.ambientParticles = c.getBoolean("ambient-particles", true);
        this.statusBossbar = c.getBoolean("status-bossbar", true);
    }

    public double chanceOnHit() {
        return chanceOnHit;
    }

    public int maxSeverity() {
        return maxSeverity;
    }

    public boolean particles() {
        return particles;
    }

    public boolean ambientParticles() {
        return ambientParticles;
    }

    public boolean statusBossbar() {
        return statusBossbar;
    }

    /** Dano por tick conforme a severidade e o modo de escala. */
    public double damageFor(int severity) {
        return switch (scaling) {
            case DAMAGE, BOTH -> damagePerTick * severity;
            case INTERVAL -> damagePerTick;
        };
    }

    /** Intervalo (ms) entre ticks de dano conforme a severidade. */
    public long intervalMillisFor(int severity) {
        double seconds = switch (scaling) {
            case INTERVAL, BOTH -> Math.max(minIntervalSeconds, baseIntervalSeconds / severity);
            case DAMAGE -> baseIntervalSeconds;
        };
        return (long) (seconds * 1000L);
    }
}
