package com.deadzone.core.profile;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Liga os eventos de ciclo de vida do jogador ao {@link ProfileManager}. */
public class ProfileListener implements Listener {

    private final ProfileManager manager;

    public ProfileListener(ProfileManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        manager.handleDeath(event.getEntity());
    }
}
