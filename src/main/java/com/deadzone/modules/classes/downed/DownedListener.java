package com.deadzone.modules.classes.downed;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.PlayerProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Liga o estado Derrubado aos eventos: intercepta o dano letal e impede que o derrubado aja. */
public class DownedListener implements Listener {

    private final DeadzonePlugin plugin;
    private final DownedManager manager;

    public DownedListener(DeadzonePlugin plugin, DownedManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /** Intercepta dano letal -> entra em Derrubado (e deixa o derrubado invulnerável). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (manager.isDowned(player.getUniqueId())) {
            event.setCancelled(true); // invulnerável enquanto derrubado
            return;
        }
        if (!manager.enabled()) {
            return;
        }
        PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
        if (profile == null || profile.isDyingFromInfection()) {
            return; // morte por infecção não usa Derrubado
        }
        if (plugin.getClassManager().config().downedIgnoredCauses().contains(event.getCause())) {
            return;
        }
        if (event.getFinalDamage() < player.getHealth()) {
            return; // não seria letal
        }
        event.setCancelled(true);
        manager.enterDowned(player, profile);
    }

    /** Derrubado não pode atacar. */
    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && manager.isDowned(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Derrubado não quebra blocos. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (manager.isDowned(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        manager.cleanup(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.cleanup(event.getPlayer());
    }
}
