package com.deadzone.modules.medicine.brokenleg;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.core.scheduler.TickService;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Perna quebrada por queda: chance crescente com a altura, aplica lentidão por um tempo.
 * A Tala (canalização imóvel de 3s) acelera a recuperação.
 */
public class BrokenLegManager {

    private static final String SPLINT_ID = "tala";

    private final DeadzonePlugin plugin;
    private final BrokenLegConfig config;

    private final Map<UUID, Long> brokenUntil = new HashMap<>();
    private final Map<UUID, Splint> splints = new HashMap<>();

    public BrokenLegManager(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.config = new BrokenLegConfig(configManager);
    }

    public void enable(TickService tickService) {
        plugin.getServer().getPluginManager().registerEvents(new BrokenLegListener(plugin, this), plugin);
        tickService.registerSecondHandler(this::tick);
    }

    public void reload() {
        config.load();
    }

    public boolean enabled() {
        return config.enabled();
    }

    public boolean isBroken(UUID uuid) {
        return brokenUntil.containsKey(uuid);
    }

    public boolean isSplinting(UUID uuid) {
        return splints.containsKey(uuid);
    }

    // ----- fratura -----

    public void handleFall(Player player, double fallDistance) {
        if (!config.enabled() || isBroken(player.getUniqueId())) {
            return;
        }
        if (fallDistance < config.minFallDistance()) {
            return;
        }
        double chance = Math.min(config.maxChance(),
                (fallDistance - config.minFallDistance()) * config.chancePerBlock());
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            breakLeg(player);
        }
    }

    private void breakLeg(Player player) {
        brokenUntil.put(player.getUniqueId(), System.currentTimeMillis() + config.recoverySeconds() * 1000L);
        applyLegSlowness(player);
        player.playSound(player, Sound.ENTITY_PLAYER_HURT, 1f, 0.6f);
        player.playSound(player, Sound.BLOCK_BONE_BLOCK_BREAK, 1f, 0.8f);
        player.sendMessage(Component.text("Você quebrou a perna! Use uma Tala para recuperar mais rápido.",
                NamedTextColor.RED));
    }

    public void tick(PlayerProfile profile) {
        UUID uuid = profile.getUuid();
        Long until = brokenUntil.get(uuid);
        if (until == null) {
            return;
        }
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null) {
            return;
        }
        if (System.currentTimeMillis() >= until) {
            brokenUntil.remove(uuid);
            removeEffect(player, "slowness");
            player.sendActionBar(Component.text("Sua perna se recuperou.", NamedTextColor.GREEN));
            return;
        }
        if (!isSplinting(uuid)) {
            applyLegSlowness(player);
        }
    }

    private void applyLegSlowness(Player player) {
        addEffect(player, "slowness", config.slownessAmplifier(), 60);
    }

    // ----- Tala (canalização) -----

    public void useSplint(Player player, ItemStack stack) {
        UUID uuid = player.getUniqueId();
        if (!isBroken(uuid)) {
            player.sendActionBar(Component.text("Sua perna não está quebrada.", NamedTextColor.GRAY));
            return;
        }
        if (isSplinting(uuid)) {
            return;
        }
        int seconds = config.splintCastSeconds();
        int totalTicks = seconds * 20;

        float originalWalkSpeed = player.getWalkSpeed();
        player.setWalkSpeed(0f);
        player.setSprinting(false);
        addEffect(player, "slowness", 255, totalTicks + 40);
        addEffect(player, "jump_boost", 128, totalTicks + 40);

        BossBar bar = BossBar.bossBar(Component.text("Aplicando tala...", NamedTextColor.WHITE),
                0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);
        player.playSound(player, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1f);

        Splint splint = new Splint(originalWalkSpeed, bar, totalTicks);
        splint.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tickSplint(player, splint), 1L, 1L);
        splints.put(uuid, splint);
    }

    private void tickSplint(Player player, Splint splint) {
        splint.elapsed++;
        splint.bar.progress(Math.min(1f, (float) splint.elapsed / splint.totalTicks));
        if (splint.elapsed >= splint.totalTicks) {
            completeSplint(player, splint);
        }
    }

    private void completeSplint(Player player, Splint splint) {
        brokenUntil.put(player.getUniqueId(),
                System.currentTimeMillis() + config.splintRecoverySeconds() * 1000L);
        consumeMainHand(player);
        player.playSound(player, Sound.BLOCK_WOOL_PLACE, 1f, 1.2f);
        player.sendActionBar(Component.text("Tala aplicada. Recuperação acelerada.", NamedTextColor.GREEN));
        finishSplint(player, splint);
    }

    public void cancelSplint(Player player) {
        Splint splint = splints.get(player.getUniqueId());
        if (splint == null) {
            return;
        }
        finishSplint(player, splint);
        player.sendActionBar(Component.text("Tala interrompida.", NamedTextColor.RED));
    }

    public void quietCleanupSplint(Player player) {
        Splint splint = splints.remove(player.getUniqueId());
        if (splint == null) {
            return;
        }
        if (splint.task != null) {
            splint.task.cancel();
        }
        player.setWalkSpeed(splint.originalWalkSpeed);
        clearImmobilize(player);
        player.hideBossBar(splint.bar);
    }

    private void finishSplint(Player player, Splint splint) {
        player.setWalkSpeed(splint.originalWalkSpeed);
        clearImmobilize(player);
        player.hideBossBar(splint.bar);
        if (splint.task != null) {
            splint.task.cancel();
        }
        splints.remove(player.getUniqueId());
    }

    public void clearLeg(UUID uuid) {
        brokenUntil.remove(uuid);
        splints.remove(uuid);
    }

    private void consumeMainHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean isSplint = plugin.getItemRegistry().resolve(hand)
                .map(item -> item.id().equals(SPLINT_ID)).orElse(false);
        if (isSplint) {
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    private void clearImmobilize(Player player) {
        removeEffect(player, "slowness");
        removeEffect(player, "jump_boost");
    }

    private void addEffect(Player player, String id, int amplifier, int ticks) {
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(id));
        if (type != null) {
            player.addPotionEffect(new PotionEffect(type, ticks, amplifier, true, false, false));
        }
    }

    private void removeEffect(Player player, String id) {
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(id));
        if (type != null) {
            player.removePotionEffect(type);
        }
    }

    private static final class Splint {
        final float originalWalkSpeed;
        final BossBar bar;
        final int totalTicks;
        int elapsed;
        BukkitTask task;

        Splint(float originalWalkSpeed, BossBar bar, int totalTicks) {
            this.originalWalkSpeed = originalWalkSpeed;
            this.bar = bar;
            this.totalTicks = totalTicks;
        }
    }
}
