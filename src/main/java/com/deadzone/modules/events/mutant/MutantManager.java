package com.deadzone.modules.events.mutant;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.entity.EntityKeys;
import com.deadzone.modules.events.EventsConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Zumbis mutantes: Corredor (rápido/fraco), Tanque (lento/resistente, repulsão)
 * e Explosivo (nuvem de veneno ao morrer).
 */
public class MutantManager {

    private final DeadzonePlugin plugin;
    private final EventsConfig config;

    public MutantManager(DeadzonePlugin plugin, EventsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public MutantType getType(LivingEntity entity) {
        String v = entity.getPersistentDataContainer().get(EntityKeys.ZOMBIE_TYPE, PersistentDataType.STRING);
        return MutantType.fromString(v);
    }

    /** Chamado no spawn de um zumbi: pode transformá-lo em mutante. */
    public void handleSpawn(LivingEntity entity) {
        if (!config.mutantsEnabled() || !plugin.getInfectionManager().isZombieType(entity)) {
            return;
        }
        if (getType(entity) != null
                || entity.getPersistentDataContainer().has(EntityKeys.HALLUCINATION, PersistentDataType.BYTE)) {
            return;
        }
        double chance = config.mutantBaseChance();
        if (plugin.getEventsManager().bloodMoon().isActive()) {
            chance *= config.bloodMoonMutantMultiplier();
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (rng.nextDouble() >= chance) {
            return;
        }
        applyType(entity, config.rollType(rng.nextDouble()));
    }

    public void applyType(LivingEntity entity, MutantType type) {
        entity.getPersistentDataContainer().set(EntityKeys.ZOMBIE_TYPE, PersistentDataType.STRING, type.name());

        double speed = config.typeDouble(type, "speed", 0.23);
        setAttr(entity, Attribute.GENERIC_MOVEMENT_SPEED, speed);

        double health = config.typeDouble(type, "health", 20.0);
        setAttr(entity, Attribute.GENERIC_MAX_HEALTH, health);
        entity.setHealth(health);

        // Sem baby zombies: força adulto sempre (ignora config legado).
        if (entity instanceof Zombie z) {
            z.setBaby(false);
        }

        if (type == MutantType.TANK) {
            setAttr(entity, Attribute.GENERIC_KNOCKBACK_RESISTANCE,
                    config.typeDouble(type, "knockback-resistance", 0.8));
        }

        if (config.mutantShowName()) {
            entity.customName(Component.text(label(type), NamedTextColor.RED));
            entity.setCustomNameVisible(true);
        }
    }

    /** Tanque empurra a vítima ao atacar. */
    public void applyTankKnockback(LivingEntity tank, LivingEntity victim) {
        double strength = config.typeDouble(MutantType.TANK, "knockback-strength", 1.4);
        Vector dir = victim.getLocation().toVector().subtract(tank.getLocation().toVector());
        if (dir.lengthSquared() == 0) {
            return;
        }
        dir = dir.normalize().multiply(strength);
        dir.setY(0.4);
        victim.setVelocity(victim.getVelocity().add(dir));
    }

    /** Explosivo solta nuvem de veneno ao morrer. */
    public void handleDeath(LivingEntity entity) {
        if (getType(entity) != MutantType.EXPLODER) {
            return;
        }
        var world = entity.getWorld();
        AreaEffectCloud cloud = world.spawn(entity.getLocation(), AreaEffectCloud.class);
        cloud.setRadius((float) config.typeDouble(MutantType.EXPLODER, "cloud-radius", 3.0));
        int duration = (int) config.typeDouble(MutantType.EXPLODER, "cloud-duration-seconds", 6) * 20;
        cloud.setDuration(duration);
        cloud.setColor(Color.fromRGB(70, 130, 30));
        PotionEffectType effect = Registry.EFFECT.get(
                NamespacedKey.minecraft(config.typeString(MutantType.EXPLODER, "cloud-effect", "poison")));
        if (effect != null) {
            int amp = (int) config.typeDouble(MutantType.EXPLODER, "cloud-amplifier", 0);
            cloud.addCustomEffect(new PotionEffect(effect, duration, amp), true);
        }
        world.spawnParticle(Particle.EXPLOSION, entity.getLocation(), 1);
    }

    private void setAttr(LivingEntity entity, Attribute attribute, double value) {
        var instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private String label(MutantType type) {
        return switch (type) {
            case RUNNER -> "Corredor";
            case TANK -> "Tanque";
            case EXPLODER -> "Explosivo";
        };
    }
}
