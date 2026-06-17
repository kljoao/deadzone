package com.deadzone.modules.firearms;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Carrega as skins cosméticas de armas (gun-skins.yml). */
public class FirearmSkinsConfig {

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, FirearmSkin> skins = new LinkedHashMap<>();

    public FirearmSkinsConfig(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    public void load() {
        skins.clear();
        FileConfiguration c = configManager.loadConfig("gun-skins.yml");
        ConfigurationSection sec = c.getConfigurationSection("skins");
        if (sec == null) {
            return;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            int cmd = s.getInt("model-data");
            if (cmd <= 0) {
                plugin.getLogger().warning("gun-skins.yml: skin '" + id + "' sem model-data válido.");
                continue;
            }
            skins.put(id.toLowerCase(Locale.ROOT), new FirearmSkin(
                    id.toLowerCase(Locale.ROOT),
                    s.getString("firearm", "").toLowerCase(Locale.ROOT),
                    s.getString("display", id),
                    cmd));
        }
    }

    public FirearmSkin get(String id) {
        return id == null ? null : skins.get(id.toLowerCase(Locale.ROOT));
    }

    /** Skins aplicáveis a uma arma específica. */
    public List<FirearmSkin> forFirearm(String firearmId) {
        List<FirearmSkin> out = new ArrayList<>();
        for (FirearmSkin skin : skins.values()) {
            if (skin.firearmId().equalsIgnoreCase(firearmId)) {
                out.add(skin);
            }
        }
        return out;
    }

    public java.util.Collection<FirearmSkin> all() {
        return skins.values();
    }
}
