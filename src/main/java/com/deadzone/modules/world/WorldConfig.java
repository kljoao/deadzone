package com.deadzone.modules.world;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Acesso tipado ao world.yml (regras de spawn de mobs). Recarregável. */
public class WorldConfig {

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;

    private boolean spawnControlEnabled;
    private Set<EntityType> allowedHostiles;
    private boolean blockPassive;
    private Set<EntityType> allowedPassive;
    private Set<SpawnReason> ignoredReasons;

    private boolean preventSunBurn;
    private boolean noBaby;
    private double zombieSpeedMultiplier;

    private boolean daySpawnEnabled;
    private int dayIntervalSeconds;
    private double dayChancePerPlayer;
    private int dayMinRadius;
    private int dayMaxRadius;
    private int dayPackMin;
    private int dayPackMax;
    private int dayMaxNearPlayer;
    private List<EntityType> dayTypes;

    public WorldConfig(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    public void load() {
        FileConfiguration c = configManager.loadConfig("world.yml");

        this.spawnControlEnabled = c.getBoolean("spawn-control.enabled", true);

        this.allowedHostiles = EnumSet.noneOf(EntityType.class);
        for (String name : c.getStringList("spawn-control.allowed-hostiles")) {
            EntityType type = parseEntity(name);
            if (type != null) {
                allowedHostiles.add(type);
            }
        }
        if (allowedHostiles.isEmpty()) {
            allowedHostiles.addAll(EnumSet.of(EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER));
        }

        // Bloqueio de mobs NÃO-hostis (animais, ambiente, neutros). Mundo zumbi: só zumbis spawnam.
        this.blockPassive = c.getBoolean("spawn-control.block-passive", true);
        this.allowedPassive = EnumSet.noneOf(EntityType.class);
        for (String name : c.getStringList("spawn-control.allowed-passive")) {
            EntityType type = parseEntity(name);
            if (type != null) {
                allowedPassive.add(type);
            }
        }

        this.ignoredReasons = EnumSet.noneOf(SpawnReason.class);
        for (String name : c.getStringList("spawn-control.ignored-reasons")) {
            try {
                ignoredReasons.add(SpawnReason.valueOf(name.toUpperCase().trim()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("SpawnReason inválido em world.yml: " + name);
            }
        }

        this.preventSunBurn = c.getBoolean("zombies.prevent-sun-burn", true);
        this.noBaby = c.getBoolean("zombies.no-baby", true);
        this.zombieSpeedMultiplier = c.getDouble("zombies.speed-multiplier", 1.05);

        this.daySpawnEnabled = c.getBoolean("zombies.day-spawn.enabled", true);
        this.dayIntervalSeconds = Math.max(1, c.getInt("zombies.day-spawn.interval-seconds", 15));
        this.dayChancePerPlayer = c.getDouble("zombies.day-spawn.chance-per-player", 0.5);
        this.dayMinRadius = Math.max(1, c.getInt("zombies.day-spawn.min-radius", 16));
        this.dayMaxRadius = Math.max(dayMinRadius + 1, c.getInt("zombies.day-spawn.max-radius", 40));
        this.dayPackMin = Math.max(1, c.getInt("zombies.day-spawn.pack-min", 1));
        this.dayPackMax = Math.max(dayPackMin, c.getInt("zombies.day-spawn.pack-max", 3));
        this.dayMaxNearPlayer = Math.max(0, c.getInt("zombies.day-spawn.max-near-player", 12));

        this.dayTypes = new ArrayList<>();
        for (String name : c.getStringList("zombies.day-spawn.types")) {
            EntityType type = parseEntity(name);
            if (type != null) {
                dayTypes.add(type);
            }
        }
        if (dayTypes.isEmpty()) {
            dayTypes.add(EntityType.ZOMBIE);
        }
    }

    private EntityType parseEntity(String name) {
        if (name == null) {
            return null;
        }
        try {
            return EntityType.valueOf(name.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("EntityType inválido em world.yml: " + name);
            return null;
        }
    }

    public boolean spawnControlEnabled() {
        return spawnControlEnabled;
    }

    public Set<EntityType> allowedHostiles() {
        return allowedHostiles;
    }

    public boolean blockPassive() {
        return blockPassive;
    }

    public Set<EntityType> allowedPassive() {
        return allowedPassive;
    }

    public Set<SpawnReason> ignoredReasons() {
        return ignoredReasons;
    }

    public boolean preventSunBurn() {
        return preventSunBurn;
    }

    public boolean noBaby() {
        return noBaby;
    }

    public double zombieSpeedMultiplier() {
        return zombieSpeedMultiplier;
    }

    public boolean daySpawnEnabled() {
        return daySpawnEnabled;
    }

    public int dayIntervalSeconds() {
        return dayIntervalSeconds;
    }

    public double dayChancePerPlayer() {
        return dayChancePerPlayer;
    }

    public int dayMinRadius() {
        return dayMinRadius;
    }

    public int dayMaxRadius() {
        return dayMaxRadius;
    }

    public int dayPackMin() {
        return dayPackMin;
    }

    public int dayPackMax() {
        return dayPackMax;
    }

    public int dayMaxNearPlayer() {
        return dayMaxNearPlayer;
    }

    public List<EntityType> dayTypes() {
        return dayTypes;
    }
}
