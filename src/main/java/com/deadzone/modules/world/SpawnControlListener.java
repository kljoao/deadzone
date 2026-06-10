package com.deadzone.modules.world;

import com.deadzone.DeadzonePlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;

/**
 * Regras de mundo: só zumbis hostis spawnam, e zumbis não pegam fogo no sol.
 */
public class SpawnControlListener implements Listener {

    private final DeadzonePlugin plugin;
    private final WorldConfig config;

    public SpawnControlListener(DeadzonePlugin plugin, WorldConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** Bloqueia o spawn de qualquer mob hostil que não esteja na lista permitida. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Zombie) {
            applySpeed(event.getEntity());
        }
        if (!config.spawnControlEnabled()) {
            return;
        }
        if (config.ignoredReasons().contains(event.getSpawnReason())) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity instanceof Enemy && !config.allowedHostiles().contains(entity.getType())) {
            event.setCancelled(true);
            return;
        }
        // Sem baby zombies: converte para adulto no spawn (e reforça no tick seguinte).
        if (config.noBaby() && entity instanceof Zombie zombie && zombie.isBaby()) {
            zombie.setBaby(false);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (zombie.isValid() && zombie.isBaby()) {
                    zombie.setBaby(false);
                }
            });
        }
    }

    /** Deixa os zumbis um pouco mais rápidos (multiplicador no atributo de velocidade). */
    private void applySpeed(LivingEntity entity) {
        double mult = config.zombieSpeedMultiplier();
        if (mult == 1.0) {
            return;
        }
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, "zombie_speed");
        for (AttributeModifier m : attr.getModifiers()) {
            if (key.equals(m.getKey())) {
                return;
            }
        }
        attr.addModifier(new AttributeModifier(key, mult - 1.0,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
    }

    // Impede combustão por luz do sol. Lava/fogo (block) e Aspecto Flamejante (entity)
    // continuam funcionando, pois têm subclasses próprias do evento.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (!config.preventSunBurn()) {
            return;
        }
        if (event instanceof EntityCombustByBlockEvent || event instanceof EntityCombustByEntityEvent) {
            return; // lava, fogo ou ataque flamejante: deixa queimar
        }
        if (config.allowedHostiles().contains(event.getEntity().getType())) {
            event.setCancelled(true);
        }
    }
}
