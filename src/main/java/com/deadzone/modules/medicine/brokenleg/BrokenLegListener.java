package com.deadzone.modules.medicine.brokenleg;

import com.deadzone.DeadzonePlugin;
import org.bukkit.GameMode;
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

public class BrokenLegListener implements Listener {

    private final DeadzonePlugin plugin;
    private final BrokenLegManager manager;

    public BrokenLegListener(DeadzonePlugin plugin, BrokenLegManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (manager.isSplinting(player.getUniqueId())) {
            manager.cancelSplint(player);
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
            return;
        }
        if (event.getFinalDamage() >= player.getHealth()) {
            return; // queda letal: deixa o sistema de morte/derrubado cuidar
        }
        manager.handleFall(player, player.getFallDistance());
    }

    @EventHandler
    public void onSwitch(PlayerItemHeldEvent event) {
        if (manager.isSplinting(event.getPlayer().getUniqueId())) {
            manager.cancelSplint(event.getPlayer());
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.isSplinting(event.getPlayer().getUniqueId())) {
            manager.cancelSplint(event.getPlayer());
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (manager.isSplinting(event.getPlayer().getUniqueId())) {
            manager.cancelSplint(event.getPlayer());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        manager.quietCleanupSplint(event.getEntity());
        manager.clearLeg(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.quietCleanupSplint(event.getPlayer());
    }
}
