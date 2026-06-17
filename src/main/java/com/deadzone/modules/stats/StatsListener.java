package com.deadzone.modules.stats;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.PlayerProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Contabiliza estatísticas vitalícias na morte: mortes do jogador, abates de outros jogadores e o
 * recorde de sobrevivência (tempo da vida que terminou). Abates de zumbi e reanimações são contados
 * na origem (XpListener e DownedManager). Roda em MONITOR — independente do wipe da economia/perfil.
 */
public class StatsListener implements Listener {

    private final DeadzonePlugin plugin;

    public StatsListener(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        PlayerProfile profile = plugin.getProfileManager().get(victim.getUniqueId());
        if (profile != null) {
            profile.addDeath();
            profile.recordDeathSurvival(System.currentTimeMillis());
        }
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            PlayerProfile killerProfile = plugin.getProfileManager().get(killer.getUniqueId());
            if (killerProfile != null) {
                killerProfile.addPlayerKill();
            }
        }
    }
}
