package com.deadzone.modules.medicine.bleeding;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.BleedState;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.core.scheduler.TickService;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sangramento: dano periódico que piora com os golpes e só para com bandagem.
 * Usa {@link Player#damage(double)} (causa CUSTOM), então não conta para o agravamento de infecção.
 */
public class BleedingManager {

    private static final Particle.DustOptions BLOOD =
            new Particle.DustOptions(Color.fromRGB(140, 0, 0), 1.2f);

    private final DeadzonePlugin plugin;
    private final BleedingConfig config;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Long> woundInfectedUntil = new HashMap<>();

    public BleedingManager(DeadzonePlugin plugin, BleedingConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void enable(TickService tickService) {
        tickService.registerSecondHandler(this::tick);
        plugin.getServer().getPluginManager().registerEvents(new BleedingListener(plugin, this), plugin);
    }

    public BleedingConfig config() {
        return config;
    }

    /** Rola a chance de sangrar ao ser atingido por um zumbi e aplica/agrava. */
    public void rollOnZombieHit(Player player, PlayerProfile profile) {
        if (ThreadLocalRandom.current().nextDouble() >= config.chanceOnHit()) {
            return;
        }
        BleedState state = profile.getBleedState();
        if (state == null) {
            long next = System.currentTimeMillis() + config.intervalMillisFor(1);
            profile.setBleedState(new BleedState(1, next));
            player.sendActionBar(Component.text("Você começou a sangrar!", NamedTextColor.RED));
            player.playSound(player, Sound.ENTITY_PLAYER_HURT, 0.6f, 1.3f);
        } else {
            state.setSeverity(Math.min(config.maxSeverity(), state.getSeverity() + 1));
        }
    }

    /** Para o sangramento (usado pela bandagem). @return true se havia o que estancar. */
    public boolean stop(PlayerProfile profile) {
        if (profile.getBleedState() == null) {
            return false;
        }
        profile.setBleedState(null);
        Player player = plugin.getServer().getPlayer(profile.getUuid());
        clearBar(profile.getUuid(), player);
        return true;
    }

    /** Tick por segundo: partículas + BossBar + dano (pausado durante a canalização da bandagem). */
    public void tick(PlayerProfile profile) {
        UUID uuid = profile.getUuid();
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            tickWound(player, uuid);
        }
        BleedState state = profile.getBleedState();

        if (state == null) {
            clearBar(uuid, player);
            return;
        }
        if (player == null) {
            return;
        }

        if (config.statusBossbar()) {
            updateBar(player, state);
        }
        if (config.ambientParticles()) {
            spawnBodyParticles(player, state);
        }

        long now = System.currentTimeMillis();
        if (now >= state.getNextDamageAt()) {
            boolean channeling = plugin.getMedicineManager().bandage().isChanneling(uuid);
            if (!channeling) {
                player.damage(config.damageFor(state.getSeverity()));
                if (config.particles()) {
                    player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0),
                            10, 0.3, 0.5, 0.3, 0, BLOOD);
                }
            }
            state.setNextDamageAt(now + config.intervalMillisFor(state.getSeverity()));
        }
    }

    /** Remove a BossBar de um jogador (saída/morte). */
    public void clear(UUID uuid) {
        woundInfectedUntil.remove(uuid);
        clearBar(uuid, plugin.getServer().getPlayer(uuid));
    }

    public void rollWoundInfection(Player player, PlayerProfile profile) {
        if (config.woundChance() <= 0) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() < config.woundChance()) {
            woundInfectedUntil.put(profile.getUuid(),
                    System.currentTimeMillis() + config.woundDurationSeconds() * 1000L);
            player.sendActionBar(Component.text("A ferida infeccionou...", NamedTextColor.DARK_RED));
            player.playSound(player, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.6f, 0.5f);
        }
    }

    public void cureWound(UUID uuid) {
        woundInfectedUntil.remove(uuid);
    }

    public boolean isWoundInfected(UUID uuid) {
        return woundInfectedUntil.containsKey(uuid);
    }

    private void tickWound(Player player, UUID uuid) {
        Long until = woundInfectedUntil.get(uuid);
        if (until == null) {
            return;
        }
        if (System.currentTimeMillis() >= until) {
            woundInfectedUntil.remove(uuid);
            player.sendActionBar(Component.text("A ferida cicatrizou.", NamedTextColor.GREEN));
            return;
        }
        addEffect(player, "weakness", 0, 60);
        if (ThreadLocalRandom.current().nextDouble() < 0.2) {
            addEffect(player, "poison", 0, 60);
        }
    }

    private void addEffect(Player player, String id, int amplifier, int ticks) {
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(id));
        if (type != null) {
            player.addPotionEffect(new PotionEffect(type, ticks, amplifier, true, false, false));
        }
    }

    private void spawnBodyParticles(Player player, BleedState state) {
        int count = 2 + state.getSeverity();
        Location base = player.getLocation().add(0, 1.0, 0);
        player.getWorld().spawnParticle(Particle.DUST, base, count, 0.25, 0.5, 0.25, 0, BLOOD);
    }

    private void updateBar(Player player, BleedState state) {
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), k -> {
            BossBar b = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
            player.showBossBar(b);
            return b;
        });
        float progress = Math.min(1f, (float) state.getSeverity() / config.maxSeverity());
        bar.progress(progress);
        bar.name(Component.text("🩸 Sangrando (nível " + state.getSeverity() + ")", NamedTextColor.RED));
    }

    private void clearBar(UUID uuid, Player player) {
        BossBar bar = bars.remove(uuid);
        if (bar != null && player != null) {
            player.hideBossBar(bar);
        }
    }
}
