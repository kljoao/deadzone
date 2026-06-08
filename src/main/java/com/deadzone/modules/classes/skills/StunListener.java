package com.deadzone.modules.classes.skills;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.PlayerClass;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.modules.classes.ClassConfig;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Bruto — Armas de Contusão: chance de atordoar (lentidão pesada) zumbis com machado/espada.
 */
public class StunListener implements Listener {

    private static final String SKILL = "bru_atordoamento";

    private final DeadzonePlugin plugin;

    public StunListener(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim) || !plugin.getInfectionManager().isZombieType(victim)) {
            return;
        }
        PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
        if (profile == null || profile.getPlayerClass() != PlayerClass.BRUTO || !profile.hasSkill(SKILL)) {
            return;
        }
        ClassConfig config = plugin.getClassManager().config();
        if (!config.stunWeapons().contains(player.getInventory().getItemInMainHand().getType())) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= config.stunChance()) {
            return;
        }
        PotionEffectType slowness = Registry.EFFECT.get(NamespacedKey.minecraft("slowness"));
        if (slowness != null) {
            victim.addPotionEffect(new PotionEffect(slowness,
                    config.stunDurationSeconds() * 20, config.stunAmplifier(), true, true, true));
        }
        victim.getWorld().spawnParticle(org.bukkit.Particle.CRIT, victim.getLocation().add(0, 1, 0), 12, 0.3, 0.3, 0.3, 0.1);
        player.playSound(player, org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 0.8f);
    }
}
