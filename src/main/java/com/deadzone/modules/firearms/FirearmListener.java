package com.deadzone.modules.firearms;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.entity.EntityKeys;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/** Coronhada (esquerdo, 100% carregado), saque ao equipar, recarga e dano dos projéteis. */
public class FirearmListener implements Listener {

    private final DeadzonePlugin plugin;
    private final FirearmManager manager;
    private boolean applyingBulletDamage;

    public FirearmListener(DeadzonePlugin plugin, FirearmManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private Optional<FirearmItem> firearmInHand(Player player) {
        return plugin.getItemRegistry().resolve(player.getInventory().getItemInMainHand())
                .filter(ci -> ci instanceof FirearmItem)
                .map(ci -> (FirearmItem) ci);
    }

    /** Coronhada: só com o golpe 100% carregado e fora do cooldown de 8s. */
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (applyingBulletDamage || !(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Optional<FirearmItem> opt = firearmInHand(player);
        if (opt.isEmpty()) {
            return;
        }
        FirearmItem firearm = opt.get();
        if (player.getAttackCooldown() < 0.9f) {
            event.setCancelled(true);
            return;
        }
        if (!manager.tryMeleeHit(player, firearm)) {
            event.setCancelled(true);
            return;
        }
        manager.applyConcussion(player, firearm, target);
    }

    /** Sacar a arma ao trocar para ela (delay sem poder atirar). */
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        manager.restoreHidden(player, event.getPreviousSlot());
        ItemStack next = player.getInventory().getItem(event.getNewSlot());
        plugin.getItemRegistry().resolve(next)
                .filter(ci -> ci instanceof FirearmItem)
                .map(ci -> (FirearmItem) ci)
                .ifPresent(firearm -> {
                    if (firearm.type() != null) {
                        manager.startDraw(player, firearm.type(), event.getNewSlot());
                    }
                });
    }

    /** Não quebrar blocos com a arma na mão (nem o progresso de mineração). */
    @EventHandler
    public void onBlockDamage(BlockDamageEvent event) {
        if (firearmInHand(event.getPlayer()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (firearmInHand(event.getPlayer()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        firearmInHand(player).ifPresent(firearm -> {
            event.setCancelled(true);
            manager.startReload(player, firearm, player.getInventory().getItemInMainHand());
        });
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball ball)) {
            return;
        }
        Double damage = ball.getPersistentDataContainer().get(EntityKeys.FIREARM_BULLET, PersistentDataType.DOUBLE);
        if (damage == null) {
            return;
        }
        Player shooter = ball.getShooter() instanceof Player p ? p : null;
        if (event.getHitEntity() instanceof LivingEntity target && target != shooter) {
            applyingBulletDamage = true;
            target.damage(damage, shooter);
            applyingBulletDamage = false;
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 6, 0.1, 0.2, 0.1, 0.02);
        }
        ball.remove();
    }
}
