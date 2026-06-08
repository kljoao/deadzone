package com.deadzone.modules.events.bloodmoon;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Durante a Lua de Sangue, buffa os zumbis que spawnam (roda em HIGH, depois do mutante).
 */
public class BloodMoonListener implements Listener {

    private final BloodMoonManager manager;

    public BloodMoonListener(BloodMoonManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (manager.isActive()) {
            manager.buff(event.getEntity());
        }
    }
}
