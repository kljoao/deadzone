package com.deadzone.modules.infection;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.PlayerProfile;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

// O agravamento (EntityDamageEvent, LOW) roda antes da chance de infecção
// (EntityDamageByEntityEvent, NORMAL), pra o golpe que infecta não agravar no mesmo instante.
public class InfectionListener implements Listener {

    private final DeadzonePlugin plugin;
    private final InfectionManager manager;

    public InfectionListener(DeadzonePlugin plugin, InfectionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /** Agravamento: tomar dano (de causa relevante) já infectado pode saltar +15%. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerProfile p = plugin.getProfileManager().get(player.getUniqueId());
        if (p == null || !p.isInfected()) {
            return;
        }
        manager.onDamageWhileInfected(p, event.getCause());
    }

    /** Chance de infectar ao ser atingido por um zumbi. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onZombieHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!manager.isZombieType(event.getDamager())) {
            return;
        }
        PlayerProfile p = plugin.getProfileManager().get(player.getUniqueId());
        if (p == null) {
            return;
        }
        p.markLastDamageByZombie();
        manager.onZombieDamage(player, p, event.getDamager());
    }

    /** Mensagem de morte customizada quando a causa foi a infecção. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerProfile p = plugin.getProfileManager().get(player.getUniqueId());
        if (p == null || !p.isDyingFromInfection()) {
            return;
        }
        event.deathMessage(plugin.getMessages().get("infection-death",
                Placeholder.unparsed("player", player.getName())));
        p.setDyingFromInfection(false);
    }
}
