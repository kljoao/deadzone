package com.deadzone.modules.events;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.scheduler.TickService;
import com.deadzone.modules.events.bloodmoon.BloodMoonManager;
import com.deadzone.modules.events.mutant.MutantLabelService;
import com.deadzone.modules.events.mutant.MutantListener;
import com.deadzone.modules.events.mutant.MutantManager;
import com.deadzone.modules.events.zone.ZoneConfig;
import com.deadzone.modules.events.zone.ZoneManager;

/** Orquestra mutantes, Lua de Sangue e zonas tóxicas. */
public class EventsManager {

    private final DeadzonePlugin plugin;
    private final EventsConfig config;
    private final MutantManager mutantManager;
    private final MutantLabelService mutantLabelService;
    private final BloodMoonManager bloodMoonManager;
    private final ZoneManager zoneManager;

    public EventsManager(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.config = new EventsConfig(plugin, configManager);
        this.mutantManager = new MutantManager(plugin, config);
        this.mutantLabelService = new MutantLabelService(plugin, mutantManager);
        this.bloodMoonManager = new BloodMoonManager(plugin, config);
        this.zoneManager = new ZoneManager(plugin, new ZoneConfig(plugin));
    }

    public void enable(TickService tickService) {
        plugin.getServer().getPluginManager().registerEvents(new MutantListener(mutantManager), plugin);
        mutantLabelService.start();
        bloodMoonManager.enable();
        zoneManager.enable(tickService);
    }

    public void disable() {
        mutantLabelService.stop();
        bloodMoonManager.disable();
    }

    public void reload() {
        config.load();
        zoneManager.reload();
    }

    public MutantManager mutant() {
        return mutantManager;
    }

    public BloodMoonManager bloodMoon() {
        return bloodMoonManager;
    }

    public ZoneManager zone() {
        return zoneManager;
    }
}
