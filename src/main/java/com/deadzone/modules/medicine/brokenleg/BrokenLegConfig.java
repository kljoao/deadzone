package com.deadzone.modules.medicine.brokenleg;

import com.deadzone.core.config.ConfigManager;
import org.bukkit.configuration.file.FileConfiguration;

public class BrokenLegConfig {

    private final ConfigManager configManager;

    private boolean enabled;
    private double minFallDistance;
    private double chancePerBlock;
    private double maxChance;
    private int slownessAmplifier;
    private int recoverySeconds;
    private int splintRecoverySeconds;
    private int splintCastSeconds;

    public BrokenLegConfig(ConfigManager configManager) {
        this.configManager = configManager;
        load();
    }

    public void load() {
        FileConfiguration c = configManager.loadConfig("brokenleg.yml");
        this.enabled = c.getBoolean("enabled", true);
        this.minFallDistance = c.getDouble("min-fall-distance", 4);
        this.chancePerBlock = c.getDouble("chance-per-block", 0.10);
        this.maxChance = c.getDouble("max-chance", 0.95);
        this.slownessAmplifier = c.getInt("slowness-amplifier", 1);
        this.recoverySeconds = Math.max(1, c.getInt("recovery-seconds", 300));
        this.splintRecoverySeconds = Math.max(1, c.getInt("splint.recovery-seconds", 60));
        this.splintCastSeconds = Math.max(1, c.getInt("splint.cast-seconds", 3));
    }

    public boolean enabled() {
        return enabled;
    }

    public double minFallDistance() {
        return minFallDistance;
    }

    public double chancePerBlock() {
        return chancePerBlock;
    }

    public double maxChance() {
        return maxChance;
    }

    public int slownessAmplifier() {
        return slownessAmplifier;
    }

    public int recoverySeconds() {
        return recoverySeconds;
    }

    public int splintRecoverySeconds() {
        return splintRecoverySeconds;
    }

    public int splintCastSeconds() {
        return splintCastSeconds;
    }
}
