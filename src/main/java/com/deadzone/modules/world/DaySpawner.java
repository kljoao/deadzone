package com.deadzone.modules.world;

import com.deadzone.DeadzonePlugin;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.concurrent.ThreadLocalRandom;

/** Spawna zumbis na superfície ao redor dos jogadores durante o dia (o spawn natural não ocorre com luz alta). */
public class DaySpawner {

    private final DeadzonePlugin plugin;
    private final WorldConfig config;

    private int taskId = -1;

    public DaySpawner(DeadzonePlugin plugin, WorldConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        stop();
        if (!config.daySpawnEnabled()) {
            return;
        }
        long period = config.dayIntervalSeconds() * 20L;
        this.taskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::run, period, period).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void run() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            World world = player.getWorld();
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            if (!world.isDayTime()) {
                continue;
            }
            GameMode mode = player.getGameMode();
            if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
                continue;
            }
            if (rng.nextDouble() >= config.dayChancePerPlayer()) {
                continue;
            }
            if (countNearbyZombies(player) >= config.dayMaxNearPlayer()) {
                continue;
            }

            int pack = rng.nextInt(config.dayPackMin(), config.dayPackMax() + 1);
            EntityType type = config.dayTypes().get(rng.nextInt(config.dayTypes().size()));
            for (int i = 0; i < pack; i++) {
                Location loc = findSpawnLocation(player, rng);
                if (loc != null) {
                    world.spawnEntity(loc, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
                }
            }
        }
    }

    private long countNearbyZombies(Player player) {
        int r = config.dayMaxRadius();
        return player.getNearbyEntities(r, r, r).stream()
                .filter(e -> config.dayTypes().contains(e.getType()))
                .count();
    }

    private Location findSpawnLocation(Player player, ThreadLocalRandom rng) {
        World world = player.getWorld();
        double angle = rng.nextDouble(Math.PI * 2);
        double dist = config.dayMinRadius() + rng.nextDouble(config.dayMaxRadius() - config.dayMinRadius());
        int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * dist);
        int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * dist);

        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null; // não força carregamento de chunk
        }

        int y = world.getHighestBlockYAt(x, z);
        Block ground = world.getBlockAt(x, y, z);
        Block above = world.getBlockAt(x, y + 1, z);
        Block above2 = world.getBlockAt(x, y + 2, z);

        if (!ground.getType().isSolid() || ground.isLiquid()) {
            return null;
        }
        if (!above.getType().isAir() || !above2.getType().isAir()) {
            return null;
        }
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }
}
