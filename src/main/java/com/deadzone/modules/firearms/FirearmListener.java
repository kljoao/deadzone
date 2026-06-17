package com.deadzone.modules.firearms;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.entity.EntityKeys;
import com.deadzone.core.profile.PlayerProfile;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
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
        event.setDamage(0); // coronhada NÃO causa dano, só contunde (concussão)
        manager.applyConcussion(player, firearm, target);
    }

    /** Clique esquerdo no ar/bloco = alterna a mira (ADS). Em entidade = coronhada (onAttack). */
    @EventHandler
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        firearmInHand(player).ifPresent(f -> manager.toggleAds(player));
    }

    /** Ao sair: limpa TODO o estado da arma (mira, rajada e timers — evita leak dos mapas). */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.clearPlayer(event.getPlayer());
    }

    /** Na morte: encerra rajada/mira e zera timers (senão a Slowness do ADS persiste no respawn). */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        manager.clearPlayer(event.getEntity());
    }

    /** Trocar de mundo cancela a mira/rajada em andamento. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        manager.clearPlayer(event.getPlayer());
    }

    /** Largar a arma no meio da rajada/mira encerra o estado. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        boolean isFirearm = plugin.getItemRegistry().resolve(dropped)
                .filter(ci -> ci instanceof FirearmItem).isPresent();
        if (isFirearm) {
            manager.clearPlayer(event.getPlayer());
        }
    }

    /** Sacar a arma ao trocar para ela (delay sem poder atirar). */
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        manager.clearAds(player); // trocar de slot cancela a mira
        manager.resetSpread(player); // recuo/cadência não vazam entre armas diferentes
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
            ItemStack gun = player.getInventory().getItemInMainHand();
            // Agachado + F numa arma automática = alterna semi/auto. Senão, recarrega.
            if (player.isSneaking() && firearm.type() != null && firearm.type().automatic()) {
                manager.toggleFireMode(player, firearm, gun);
            } else {
                manager.startReload(player, firearm, gun);
            }
        });
    }

    /** A arma vira "comida" no modo auto só p/ detectar o segurar — nunca deve ser de fato consumida. */
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        plugin.getItemRegistry().resolve(event.getItem())
                .filter(ci -> ci instanceof FirearmItem)
                .ifPresent(ci -> event.setCancelled(true));
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
            // Zera os i-frames (invulnerabilidade) antes de cada bala: senão, numa rajada, só o 1º
            // tiro de cada janela de ~0.5s registra (o vanilla ignora dano <= ao último na janela).
            target.setNoDamageTicks(0);
            target.setLastDamage(0);
            target.damage(damage, shooter);
            applyingBulletDamage = false;
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 6, 0.1, 0.2, 0.1, 0.02);
            if (target instanceof Player victim) {
                PlayerProfile profile = plugin.getProfileManager().get(victim.getUniqueId());
                if (profile != null) {
                    plugin.getMedicineManager().bleeding().rollOnFirearmHit(victim, profile);
                }
            }
        }
        ball.remove();
    }
}
