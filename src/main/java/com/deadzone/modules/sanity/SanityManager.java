package com.deadzone.modules.sanity;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.core.scheduler.TickService;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Sistema de sanidade: cai no escuro ou cercado por zumbis; recupera em área
 * iluminada, base, companhia ou de dia. Penaliza dano corpo a corpo e dá lentidão.
 */
public class SanityManager {

    private final DeadzonePlugin plugin;
    private final SanityConfig config;

    public SanityManager(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.config = new SanityConfig(plugin, configManager);
    }

    public void enable(TickService tickService) {
        plugin.getZombieRadar().ensureRadius(config.zombieRadius());
        tickService.registerSecondHandler(this::tick);
        plugin.getServer().getPluginManager().registerEvents(new SanityListener(plugin, this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new TraumaListener(plugin, this), plugin);
    }

    public void reload() {
        config.load();
    }

    public SanityConfig config() {
        return config;
    }

    /** Boost/penalidade direta (usado por medicamentos). */
    public void add(PlayerProfile profile, double amount) {
        profile.setSanity(profile.getSanity() + amount);
    }

    public void tick(PlayerProfile profile) {
        Player player = plugin.getServer().getPlayer(profile.getUuid());
        if (player == null) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
            return;
        }

        boolean lit = isLit(player);
        boolean holdingLight = holdingLight(player);
        double delta = 0;

        if (!lit && !holdingLight) {
            delta += config.darknessLoss();
        }
        int zombies = countZombies(player);
        if (zombies > config.zombieThreshold()) {
            delta += (zombies - config.zombieThreshold()) * config.perZombieLoss();
        }
        if (lit) {
            delta += config.litAreaGain();
        }
        if (plugin.getClaimManager().isInOwnBase(player)) {
            delta += config.baseBonus();
        }
        if (hasCompany(player)) {
            delta += config.companyGain();
        }
        if (player.getWorld().isDayTime()) {
            delta += config.daylightGain();
        }

        double now = clamp(profile.getSanity() + delta);
        profile.setSanity(now);

        applyEffects(player, now); // sanidade aparece só no scoreboard (HUD), sem boss bar
    }

    private boolean isLit(Player player) {
        Block b = player.getLocation().getBlock();
        if (player.getWorld().isDayTime() && b.getLightFromSky() >= config.lightThreshold()) {
            return true;
        }
        return b.getLightFromBlocks() >= config.lightThreshold();
    }

    private boolean holdingLight(Player player) {
        return config.lightItems().contains(player.getInventory().getItemInMainHand().getType())
                || config.lightItems().contains(player.getInventory().getItemInOffHand().getType());
    }

    private int countZombies(Player player) {
        return plugin.getZombieRadar().count(player, config.zombieRadius());
    }

    private boolean hasCompany(Player player) {
        int r = config.companyRadius();
        for (Player other : player.getWorld().getPlayers()) {
            if (!other.equals(player) && other.getLocation().distanceSquared(player.getLocation()) <= (double) r * r) {
                return true;
            }
        }
        return false;
    }

    private void applyEffects(Player player, double sanity) {
        SanityConfig.EffectTier tier = config.tierFor(sanity);
        if (tier == null) {
            return;
        }
        if (tier.slowness() >= 0) {
            addEffect(player, "slowness", tier.slowness(), 60);
        }
        if (tier.visual() && ThreadLocalRandom.current().nextDouble() < 0.15) {
            addEffect(player, "nausea", 0, 120);
        }
    }

    private void addEffect(Player player, String id, int amplifier, int ticks) {
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(id));
        if (type != null) {
            player.addPotionEffect(new PotionEffect(type, ticks, amplifier, true, false, false));
        }
    }

    private double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }
}
