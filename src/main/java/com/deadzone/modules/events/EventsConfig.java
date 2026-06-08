package com.deadzone.modules.events;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.modules.events.mutant.MutantType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Acesso tipado ao events.yml (mutantes + Lua de Sangue).
 */
public class EventsConfig {

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;

    private boolean mutantsEnabled;
    private double mutantBaseChance;
    private double bloodMoonMutantMultiplier;
    private boolean mutantShowName;
    private Map<MutantType, Double> weights;
    private Map<MutantType, ConfigurationSection> typeSections;

    private boolean bloodMoonEnabled;
    private int everyXDays;
    private long bloodMoonRewardXp;
    private String announceStart;
    private String announceStartSub;
    private String announceEnd;
    private double bmSpeedMult;
    private double bmHealthMult;
    private int bmSpawnInterval;
    private int bmExtraPerPlayer;
    private int bmMinRadius;
    private int bmRadius;
    private int bmCapPerPlayer;
    private boolean bmBreakEnabled;
    private double bmBreakChance;
    private int bmBreakInterval;
    private int bmBreakRadius;
    private List<Material> bmBreakBlocks;

    public EventsConfig(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    public void load() {
        FileConfiguration c = configManager.loadConfig("events.yml");

        this.mutantsEnabled = c.getBoolean("mutants.enabled", true);
        this.mutantBaseChance = c.getDouble("mutants.base-chance", 0.12);
        this.bloodMoonMutantMultiplier = c.getDouble("mutants.blood-moon-multiplier", 2.0);
        this.mutantShowName = c.getBoolean("mutants.show-name", false);

        this.weights = new EnumMap<>(MutantType.class);
        ConfigurationSection w = c.getConfigurationSection("mutants.weights");
        if (w != null) {
            for (String key : w.getKeys(false)) {
                MutantType type = MutantType.fromString(key);
                if (type != null) {
                    weights.put(type, w.getDouble(key));
                }
            }
        }
        this.typeSections = new EnumMap<>(MutantType.class);
        for (MutantType type : MutantType.values()) {
            ConfigurationSection s = c.getConfigurationSection("mutants." + type.name().toLowerCase());
            if (s != null) {
                typeSections.put(type, s);
            }
        }

        this.bloodMoonEnabled = c.getBoolean("blood-moon.enabled", true);
        this.everyXDays = Math.max(1, c.getInt("blood-moon.every-x-days", 7));
        this.bloodMoonRewardXp = c.getLong("blood-moon.reward-xp", 100);
        this.announceStart = c.getString("blood-moon.announce-start", "<dark_red>LUA DE SANGUE");
        this.announceStartSub = c.getString("blood-moon.announce-start-sub", "");
        this.announceEnd = c.getString("blood-moon.announce-end", "<gold>A Lua de Sangue passou.");
        this.bmSpeedMult = c.getDouble("blood-moon.zombie.speed-multiplier", 1.35);
        this.bmHealthMult = c.getDouble("blood-moon.zombie.health-multiplier", 1.5);
        this.bmSpawnInterval = Math.max(1, c.getInt("blood-moon.spawn.interval-seconds", 12));
        this.bmExtraPerPlayer = c.getInt("blood-moon.spawn.extra-per-player", 4);
        this.bmMinRadius = c.getInt("blood-moon.spawn.min-radius", 10);
        this.bmRadius = c.getInt("blood-moon.spawn.radius", 28);
        this.bmCapPerPlayer = c.getInt("blood-moon.spawn.cap-per-player", 30);
        this.bmBreakEnabled = c.getBoolean("blood-moon.break-blocks.enabled", true);
        this.bmBreakChance = c.getDouble("blood-moon.break-blocks.chance", 0.15);
        this.bmBreakInterval = Math.max(1, c.getInt("blood-moon.break-blocks.interval-seconds", 4));
        this.bmBreakRadius = c.getInt("blood-moon.break-blocks.radius", 16);
        this.bmBreakBlocks = new ArrayList<>();
        for (String name : c.getStringList("blood-moon.break-blocks.blocks")) {
            Material m = Material.matchMaterial(name);
            if (m != null) {
                bmBreakBlocks.add(m);
            }
        }
    }

    public double typeDouble(MutantType type, String key, double def) {
        ConfigurationSection s = typeSections.get(type);
        return s != null ? s.getDouble(key, def) : def;
    }

    public boolean typeBool(MutantType type, String key, boolean def) {
        ConfigurationSection s = typeSections.get(type);
        return s != null ? s.getBoolean(key, def) : def;
    }

    public String typeString(MutantType type, String key, String def) {
        ConfigurationSection s = typeSections.get(type);
        return s != null ? s.getString(key, def) : def;
    }

    /** Sorteia um tipo conforme os pesos. */
    public MutantType rollType(double roll01) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return MutantType.RUNNER;
        }
        double r = roll01 * total;
        double acc = 0;
        for (Map.Entry<MutantType, Double> e : weights.entrySet()) {
            acc += e.getValue();
            if (r <= acc) {
                return e.getKey();
            }
        }
        return MutantType.RUNNER;
    }

    public boolean mutantsEnabled() { return mutantsEnabled; }
    public double mutantBaseChance() { return mutantBaseChance; }
    public double bloodMoonMutantMultiplier() { return bloodMoonMutantMultiplier; }
    public boolean mutantShowName() { return mutantShowName; }

    public boolean bloodMoonEnabled() { return bloodMoonEnabled; }
    public int everyXDays() { return everyXDays; }
    public long bloodMoonRewardXp() { return bloodMoonRewardXp; }
    public String announceStart() { return announceStart; }
    public String announceStartSub() { return announceStartSub; }
    public String announceEnd() { return announceEnd; }
    public double bmSpeedMult() { return bmSpeedMult; }
    public double bmHealthMult() { return bmHealthMult; }
    public int bmSpawnInterval() { return bmSpawnInterval; }
    public int bmExtraPerPlayer() { return bmExtraPerPlayer; }
    public int bmMinRadius() { return bmMinRadius; }
    public int bmRadius() { return bmRadius; }
    public int bmCapPerPlayer() { return bmCapPerPlayer; }
    public boolean bmBreakEnabled() { return bmBreakEnabled; }
    public double bmBreakChance() { return bmBreakChance; }
    public int bmBreakInterval() { return bmBreakInterval; }
    public int bmBreakRadius() { return bmBreakRadius; }
    public List<Material> bmBreakBlocks() { return bmBreakBlocks; }
}
