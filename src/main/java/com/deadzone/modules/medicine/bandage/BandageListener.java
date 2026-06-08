package com.deadzone.modules.medicine.bandage;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Interrompe a canalização da bandagem em dano, troca de item, drop, teleporte, morte ou saída. */
public class BandageListener implements Listener {

    private final BandageService service;

    public BandageListener(BandageService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && service.isChanneling(player.getUniqueId())) {
            service.cancel(player);
        }
    }

    @EventHandler
    public void onSwitch(PlayerItemHeldEvent event) {
        if (service.isChanneling(event.getPlayer().getUniqueId())) {
            service.cancel(event.getPlayer());
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (service.isChanneling(event.getPlayer().getUniqueId())) {
            service.cancel(event.getPlayer());
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (service.isChanneling(event.getPlayer().getUniqueId())) {
            service.cancel(event.getPlayer());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        service.quietCleanup(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.quietCleanup(event.getPlayer());
    }
}
