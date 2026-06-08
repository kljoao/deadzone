package com.deadzone.modules.events.mutant;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Liga os mutantes aos eventos: transformação no spawn, nuvem do Explosivo na morte
 * e repulsão do Tanque ao atacar.
 */
public class MutantListener implements Listener {

    private final MutantManager manager;

    public MutantListener(MutantManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        manager.handleSpawn(event.getEntity());
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        manager.handleDeath(event.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity damager
                && event.getEntity() instanceof LivingEntity victim
                && manager.getType(damager) == MutantType.TANK) {
            manager.applyTankKnockback(damager, victim);
        }
    }
}
