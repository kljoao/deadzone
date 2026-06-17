package com.deadzone.modules.noise;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.core.scheduler.TickService;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Acumula "barulho" das ações do jogador (quebrar blocos, correr sem agachar).
 * Quando estoura o limite, atrai os zumbis próximos.
 */
public class NoiseManager implements Listener {

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;
    private final Map<UUID, Double> noise = new HashMap<>();

    private boolean enabled;
    private double threshold;
    private double decayPerSecond;
    private double sprintPerSecond;
    private double jump;
    private int attractRadius;

    public NoiseManager(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    public void enable(TickService tickService) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        tickService.registerSecondHandler(this::tick);
    }

    public void reload() {
        load();
    }

    private void load() {
        FileConfiguration c = configManager.loadConfig("noise.yml");
        this.enabled = c.getBoolean("enabled", true);
        this.threshold = c.getDouble("threshold", 25);
        this.decayPerSecond = c.getDouble("decay-per-second", 2);
        this.sprintPerSecond = c.getDouble("sprint-per-second", 3);
        this.jump = c.getDouble("jump", 2);
        this.attractRadius = c.getInt("attract-radius", 24);
    }

    @EventHandler(ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        if (enabled && isSurvival(event.getPlayer())) {
            add(event.getPlayer().getUniqueId(), jump);
        }
    }

    private void tick(PlayerProfile profile) {
        if (!enabled) {
            return;
        }
        Player player = plugin.getServer().getPlayer(profile.getUuid());
        if (player == null || !isSurvival(player)) {
            return;
        }
        double level = noise.getOrDefault(profile.getUuid(), 0.0);
        if (player.isSprinting() && !player.isSneaking()) {
            level += sprintPerSecond;
        }
        level = Math.max(0, level - decayPerSecond);

        if (level >= threshold) {
            attract(player);
            level *= 0.5;
        }
        noise.put(profile.getUuid(), level);
    }

    private void add(UUID uuid, double amount) {
        noise.merge(uuid, amount, Double::sum);
    }

    /** Barulho alto e pontual (ex.: tiro): enche o medidor e atrai zumbis num raio. */
    public void report(Player player, double amount, int radius) {
        if (!enabled) {
            return;
        }
        add(player.getUniqueId(), amount);
        attractInRadius(player, radius);
    }

    private void attract(Player player) {
        attractInRadius(player, attractRadius);
    }

    private void attractInRadius(Player player, int radius) {
        for (var entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Mob mob && plugin.getInfectionManager().isZombieType(mob)) {
                mob.setTarget(player);
            }
        }
    }

    private boolean isSurvival(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        noise.remove(event.getPlayer().getUniqueId());
    }
}
