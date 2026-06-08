package com.deadzone.modules.world;

import com.deadzone.DeadzonePlugin;

/** Módulo de regras de mundo: controle de spawn, anti-sun-burn e spawn diurno de zumbis. */
public class WorldManager {

    private final DeadzonePlugin plugin;
    private final WorldConfig config;
    private DaySpawner daySpawner;

    public WorldManager(DeadzonePlugin plugin, WorldConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(new SpawnControlListener(plugin, config), plugin);
        this.daySpawner = new DaySpawner(plugin, config);
        this.daySpawner.start();
    }

    public void disable() {
        if (daySpawner != null) {
            daySpawner.stop();
        }
    }

    /** Recarrega a config e reinicia o spawner diurno (chamado pelo /deadzone reload). */
    public void reload() {
        config.load();
        if (daySpawner != null) {
            daySpawner.start();
        }
    }
}
