package com.deadzone.modules.medicine.bleeding;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.PlayerProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Inicia ou agrava o sangramento ao ser atingido por um zumbi. */
public class BleedingListener implements Listener {

    private final DeadzonePlugin plugin;
    private final BleedingManager manager;

    public BleedingListener(DeadzonePlugin plugin, BleedingManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onZombieHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!plugin.getInfectionManager().isZombieType(event.getDamager())) {
            return;
        }
        PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
        if (profile == null) {
            return;
        }
        manager.rollOnZombieHit(player, profile);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        manager.clear(event.getEntity().getUniqueId());
    }
}

