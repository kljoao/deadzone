package com.deadzone.modules.sanity;

import com.deadzone.DeadzonePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Armazena o "lar" (base) de cada jogador em bases.yml. Usado pela recuperação de sanidade.
 * Fallback enquanto não integramos com um plugin de claim.
 */
public class BaseService {

    private final DeadzonePlugin plugin;
    private final File file;
    private final Map<UUID, Location> bases = new HashMap<>();

    public BaseService(DeadzonePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bases.yml");
        load();
    }

    private void load() {
        bases.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String worldName = cfg.getString(key + ".world");
                World world = worldName != null ? Bukkit.getWorld(worldName) : null;
                if (world == null) {
                    continue;
                }
                bases.put(uuid, new Location(world,
                        cfg.getDouble(key + ".x"), cfg.getDouble(key + ".y"), cfg.getDouble(key + ".z")));
            } catch (IllegalArgumentException ignored) {
                // chave inválida
            }
        }
    }

    private void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Location> e : bases.entrySet()) {
            Location loc = e.getValue();
            String base = e.getKey().toString();
            cfg.set(base + ".world", loc.getWorld().getName());
            cfg.set(base + ".x", loc.getX());
            cfg.set(base + ".y", loc.getY());
            cfg.set(base + ".z", loc.getZ());
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Falha ao salvar bases.yml: " + ex.getMessage());
        }
    }

    public void setBase(Player player) {
        bases.put(player.getUniqueId(), player.getLocation());
        save();
    }

    public void removeBase(UUID uuid) {
        if (bases.remove(uuid) != null) {
            save();
        }
    }

    public Location getBase(UUID uuid) {
        return bases.get(uuid);
    }

    public boolean isInBase(Player player, int radius) {
        Location base = bases.get(player.getUniqueId());
        if (base == null || base.getWorld() == null || !base.getWorld().equals(player.getWorld())) {
            return false;
        }
        return base.distanceSquared(player.getLocation()) <= (double) radius * radius;
    }
}
