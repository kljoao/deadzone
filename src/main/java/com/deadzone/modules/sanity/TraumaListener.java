package com.deadzone.modules.sanity;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Presenciar mortes e matar jogadores derruba a sanidade. Acima do limite de mortes,
 * o jogador fica insensível e matar não baixa mais.
 */
public class TraumaListener implements Listener {

    private final DeadzonePlugin plugin;
    private final SanityManager manager;
    private final Map<UUID, Integer> kills = new HashMap<>();

    public TraumaListener(DeadzonePlugin plugin, SanityManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        SanityConfig config = manager.config();

        for (Player witness : victim.getWorld().getNearbyPlayers(victim.getLocation(), config.traumaWitnessRadius())) {
            if (witness.equals(victim) || witness.equals(killer)) {
                continue;
            }
            PlayerProfile profile = plugin.getProfileManager().get(witness.getUniqueId());
            if (profile != null) {
                manager.add(profile, -config.traumaWitnessLoss());
            }
        }

        if (killer != null && !killer.equals(victim)) {
            int count = kills.merge(killer.getUniqueId(), 1, Integer::sum);
            if (count <= config.traumaKillThreshold()) {
                PlayerProfile profile = plugin.getProfileManager().get(killer.getUniqueId());
                if (profile != null) {
                    manager.add(profile, -config.traumaKillLoss());
                    killer.sendActionBar(Component.text("O peso na sua consciência aumenta...", NamedTextColor.DARK_RED));
                }
            }
        }

        kills.remove(victim.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        kills.remove(event.getPlayer().getUniqueId());
    }
}
