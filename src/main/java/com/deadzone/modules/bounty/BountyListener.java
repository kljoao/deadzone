package com.deadzone.modules.bounty;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Coleta/notoriedade de bounty quando um jogador mata outro. */
public class BountyListener implements Listener {

    private final BountyManager manager;

    public BountyListener(BountyManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            manager.onPvpKill(killer, victim);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.clearCache(event.getPlayer().getUniqueId());
    }
}
