package com.deadzone.modules.sanity;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.PlayerProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Aplica a penalidade de dano corpo a corpo conforme a sanidade.
 */
public class SanityListener implements Listener {

    private final DeadzonePlugin plugin;
    private final SanityManager manager;

    public SanityListener(DeadzonePlugin plugin, SanityManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
        if (profile == null) {
            return;
        }
        double factor = manager.config().meleeMultiplier(profile.getSanity());
        if (factor < 1.0) {
            event.setDamage(event.getDamage() * factor);
        }
    }
}
