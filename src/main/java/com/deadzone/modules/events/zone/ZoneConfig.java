package com.deadzone.modules.events.zone;

import com.deadzone.DeadzonePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Carrega/salva as zonas tóxicas em zones.yml (seção "zones" por nome).
 */
public class ZoneConfig {

    private final DeadzonePlugin plugin;
    private final File file;
    private List<ToxicZone> zones = new ArrayList<>();

    public ZoneConfig(DeadzonePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "zones.yml");
        if (!file.exists() && plugin.getResource("zones.yml") != null) {
            plugin.saveResource("zones.yml", false);
        }
        load();
    }

    public void load() {
        List<ToxicZone> list = new ArrayList<>();
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("zones");
        if (root != null) {
            for (String name : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(name);
                if (s == null) {
                    continue;
                }
                list.add(new ToxicZone(
                        name,
                        s.getString("world", "world"),
                        s.getInt("min.x"), s.getInt("min.y"), s.getInt("min.z"),
                        s.getInt("max.x"), s.getInt("max.y"), s.getInt("max.z"),
                        s.getDouble("dps", 2.0),
                        s.getString("protection", "gas_mask"),
                        s.getStringList("effects")));
            }
        }
        this.zones = list;
    }

    public List<ToxicZone> zones() {
        return zones;
    }

    public void add(ToxicZone zone) {
        zones.removeIf(z -> z.name().equalsIgnoreCase(zone.name()));
        zones.add(zone);
        save();
    }

    public boolean remove(String name) {
        boolean removed = zones.removeIf(z -> z.name().equalsIgnoreCase(name));
        if (removed) {
            save();
        }
        return removed;
    }

    private void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (ToxicZone z : zones) {
            String base = "zones." + z.name();
            cfg.set(base + ".world", z.world());
            cfg.set(base + ".min.x", z.minX());
            cfg.set(base + ".min.y", z.minY());
            cfg.set(base + ".min.z", z.minZ());
            cfg.set(base + ".max.x", z.maxX());
            cfg.set(base + ".max.y", z.maxY());
            cfg.set(base + ".max.z", z.maxZ());
            cfg.set(base + ".dps", z.damagePerSecond());
            cfg.set(base + ".protection", z.protectionItemId());
            cfg.set(base + ".effects", z.effects());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Falha ao salvar zones.yml: " + e.getMessage());
        }
    }
}
