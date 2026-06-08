package com.deadzone.modules.classes;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.PlayerClass;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.modules.classes.gui.ClassMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Abre o menu de seleção de classe quando o jogador está sem classe (NONE). */
public class ClassRespawnListener implements Listener {

    private final DeadzonePlugin plugin;
    private final ClassManager manager;

    public ClassRespawnListener(DeadzonePlugin plugin, ClassManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        openIfNoClass(event.getPlayer(), 10L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        openIfNoClass(event.getPlayer(), 40L); // dá tempo do perfil carregar (async)
    }

    private void openIfNoClass(Player player, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
            if (profile != null && profile.getPlayerClass() == PlayerClass.NONE) {
                new ClassMenu(plugin).open(player);
            }
        }, delayTicks);
    }
}
