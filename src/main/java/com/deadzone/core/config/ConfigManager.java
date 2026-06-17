package com.deadzone.core.config;

import com.deadzone.DeadzonePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Carrega e expõe de forma tipada o config.yml e os demais YAML dos módulos. */
public class ConfigManager {

    private final DeadzonePlugin plugin;
    private FileConfiguration config;

    public ConfigManager(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    /** Acesso bruto ao config.yml principal. */
    public FileConfiguration raw() {
        return config;
    }

    /**
     * Carrega (e copia do jar, se necessário) um arquivo YAML auxiliar da pasta do plugin.
     * Usado por módulos para seus próprios arquivos (infection.yml, sanity.yml, etc.).
     */
    public FileConfiguration loadConfig(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists() && plugin.getResource(name) != null) {
            plugin.saveResource(name, false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // Mescla chaves novas do default embutido no jar (auto-migração de configs).
        InputStream resource = plugin.getResource(name);
        if (resource != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
            cfg.setDefaults(defaults);
            cfg.options().copyDefaults(true);
            try {
                cfg.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Não foi possível mesclar defaults em " + name + ": " + e.getMessage());
            }
        }
        return cfg;
    }

    public boolean debug() {
        return config.getBoolean("debug", false);
    }

    public int dbPoolSize() {
        return Math.max(1, config.getInt("database.pool-size", 4));
    }

    /** "sqlite" (padrao) ou "postgresql". */
    public String dbType() {
        return config.getString("database.type", "sqlite").trim().toLowerCase();
    }

    public String pgHost() {
        return config.getString("database.postgresql.host", "localhost");
    }

    public int pgPort() {
        return config.getInt("database.postgresql.port", 5432);
    }

    public String pgDatabase() {
        return config.getString("database.postgresql.database", "deadzone");
    }

    public String pgUsername() {
        return config.getString("database.postgresql.username", "deadzone");
    }

    public String pgPassword() {
        return config.getString("database.postgresql.password", "");
    }

    public boolean pgSsl() {
        return config.getBoolean("database.postgresql.ssl", false);
    }

    public int autosaveMinutes() {
        return Math.max(1, config.getInt("profiles.autosave-minutes", 5));
    }

    public boolean wipeEnabled() {
        return config.getBoolean("wipe-on-death.enabled", true);
    }

    public boolean wipeResetXp() {
        return config.getBoolean("wipe-on-death.reset-xp", true);
    }

    public boolean keepTotalXpStat() {
        return config.getBoolean("wipe-on-death.keep-total-xp-stat", true);
    }
}
