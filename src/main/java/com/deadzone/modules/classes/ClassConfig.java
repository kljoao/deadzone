package com.deadzone.modules.classes;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Acesso tipado ao classes.yml. Recarregável. */
public class ClassConfig {

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;

    private boolean allowChange;

    private long xpZombieKill;
    private Map<String, Long> xpMutantKill;
    private long xpReviveAlly;

    private boolean downedEnabled;
    private int downedDurationSeconds;
    private double reviveHealthPercent;
    private Set<DamageCause> downedIgnoredCauses;

    private int diagnosisRange;

    private double stunChance;
    private int stunAmplifier;
    private int stunDurationSeconds;
    private Set<Material> stunWeapons;

    private int radioRadius;
    private int radioDurationSeconds;
    private int radioCooldownSeconds;
    private Set<Material> radioContainers;

    private double crowbarDamage;
    private double crowbarAttackSpeed;
    private int crowbarDurability;
    private int crowbarDurabilityPerHit;
    private int crowbarDurabilityPerUnlock;

    public ClassConfig(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    public void load() {
        FileConfiguration c = configManager.loadConfig("classes.yml");

        this.allowChange = c.getBoolean("class-change.allow-change", false);

        this.xpZombieKill = c.getLong("xp-sources.zombie-kill", 5);
        this.xpMutantKill = new HashMap<>();
        ConfigurationSection mk = c.getConfigurationSection("xp-sources.mutant-kill");
        if (mk != null) {
            for (String key : mk.getKeys(false)) {
                xpMutantKill.put(key.toUpperCase(), mk.getLong(key));
            }
        }
        this.xpReviveAlly = c.getLong("xp-sources.revive-ally", 25);

        this.downedEnabled = c.getBoolean("downed.enabled", true);
        this.downedDurationSeconds = Math.max(1, c.getInt("downed.duration-seconds", 30));
        this.reviveHealthPercent = c.getDouble("downed.revive-health-percent", 50);
        this.downedIgnoredCauses = EnumSet.noneOf(DamageCause.class);
        for (String name : c.getStringList("downed.ignored-causes")) {
            try {
                downedIgnoredCauses.add(DamageCause.valueOf(name.toUpperCase().trim()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("DamageCause inválido em classes.yml: " + name);
            }
        }

        this.diagnosisRange = c.getInt("diagnosis.range", 3);

        this.stunChance = c.getDouble("stun.chance", 0.20);
        this.stunAmplifier = c.getInt("stun.slowness-amplifier", 3);
        this.stunDurationSeconds = c.getInt("stun.duration-seconds", 3);
        this.stunWeapons = parseMaterials(c.getStringList("stun.weapons"));

        this.radioRadius = c.getInt("radio.radius", 10);
        this.radioDurationSeconds = c.getInt("radio.duration-seconds", 6);
        this.radioCooldownSeconds = c.getInt("radio.cooldown-seconds", 1800);
        this.radioContainers = parseMaterials(c.getStringList("radio.containers"));

        this.crowbarDamage = c.getDouble("crowbar.damage", 4.0);
        this.crowbarAttackSpeed = c.getDouble("crowbar.attack-speed", 1.6);
        this.crowbarDurability = Math.max(1, c.getInt("crowbar.durability", 64));
        this.crowbarDurabilityPerHit = c.getInt("crowbar.durability-per-hit", 1);
        this.crowbarDurabilityPerUnlock = c.getInt("crowbar.durability-per-unlock", 3);
    }

    private Set<Material> parseMaterials(List<String> names) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material m = Material.matchMaterial(name);
            if (m != null) {
                set.add(m);
            } else {
                plugin.getLogger().warning("Material inválido em classes.yml: " + name);
            }
        }
        return set;
    }

    public boolean allowChange() {
        return allowChange;
    }

    public long xpZombieKill() {
        return xpZombieKill;
    }

    public long xpMutantKill(String type) {
        return xpMutantKill.getOrDefault(type.toUpperCase(), xpZombieKill);
    }

    public long xpReviveAlly() {
        return xpReviveAlly;
    }

    public boolean downedEnabled() {
        return downedEnabled;
    }

    public int downedDurationSeconds() {
        return downedDurationSeconds;
    }

    public double reviveHealthPercent() {
        return reviveHealthPercent;
    }

    public Set<DamageCause> downedIgnoredCauses() {
        return downedIgnoredCauses;
    }

    public int diagnosisRange() {
        return diagnosisRange;
    }

    public double stunChance() {
        return stunChance;
    }

    public int stunAmplifier() {
        return stunAmplifier;
    }

    public int stunDurationSeconds() {
        return stunDurationSeconds;
    }

    public Set<Material> stunWeapons() {
        return stunWeapons;
    }

    public int radioRadius() {
        return radioRadius;
    }

    public int radioDurationSeconds() {
        return radioDurationSeconds;
    }

    public int radioCooldownSeconds() {
        return radioCooldownSeconds;
    }

    public Set<Material> radioContainers() {
        return radioContainers;
    }

    public double crowbarDamage() {
        return crowbarDamage;
    }

    public double crowbarAttackSpeed() {
        return crowbarAttackSpeed;
    }

    public int crowbarDurability() {
        return crowbarDurability;
    }

    public int crowbarDurabilityPerHit() {
        return crowbarDurabilityPerHit;
    }

    public int crowbarDurabilityPerUnlock() {
        return crowbarDurabilityPerUnlock;
    }
}
