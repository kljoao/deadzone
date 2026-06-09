package com.deadzone.modules.hud;

import com.deadzone.core.config.ConfigManager;
import org.bukkit.configuration.file.FileConfiguration;

public class HudConfig {

    private final ConfigManager configManager;

    private boolean enabled;
    private int animationInterval;
    private int barLength;
    private String title;
    private String footer;

    public HudConfig(ConfigManager configManager) {
        this.configManager = configManager;
        load();
    }

    public void load() {
        FileConfiguration c = configManager.loadConfig("hud.yml");
        this.enabled = c.getBoolean("enabled", true);
        this.animationInterval = Math.max(1, c.getInt("animation-interval-ticks", 3));
        this.barLength = Math.max(4, c.getInt("bar-length", 10));
        this.title = c.getString("title", "DEADZONE");
        this.footer = c.getString("footer", "<dark_gray>deadzone");
    }

    public boolean enabled() {
        return enabled;
    }

    public int animationInterval() {
        return animationInterval;
    }

    public int barLength() {
        return barLength;
    }

    public String title() {
        return title;
    }

    public String footer() {
        return footer;
    }
}
