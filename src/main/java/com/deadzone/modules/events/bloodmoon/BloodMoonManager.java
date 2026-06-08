package com.deadzone.modules.events.bloodmoon;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.entity.EntityKeys;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.modules.events.EventsConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lua de Sangue: noites em que a horda fica mais numerosa, rápida, resistente
 * e capaz de quebrar blocos fracos. Recompensa quem sobrevive.
 */
public class BloodMoonManager {

    private final DeadzonePlugin plugin;
    private final EventsConfig config;

    private boolean active;
    private long lastTriggerDay = -1;
    private int scheduleTaskId = -1;
    private int pressureTaskId = -1;
    private int breakTaskId = -1;

    public BloodMoonManager(DeadzonePlugin plugin, EventsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean isActive() {
        return active;
    }

    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(new BloodMoonListener(this), plugin);
        this.scheduleTaskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::checkSchedule, 100L, 100L).getTaskId();
    }

    public void disable() {
        cancel(scheduleTaskId);
        stopTasks();
        scheduleTaskId = -1;
    }

    private void checkSchedule() {
        if (!config.bloodMoonEnabled() || Bukkit.getWorlds().isEmpty()) {
            return;
        }
        World world = Bukkit.getWorlds().get(0);
        long day = world.getFullTime() / 24000L;
        boolean night = world.getTime() >= 13000L;

        if (!active && night && day != lastTriggerDay && day % config.everyXDays() == 0) {
            start(day);
        } else if (active && !night) {
            end();
        }
    }

    public void forceStart() {
        if (!active) {
            long day = Bukkit.getWorlds().isEmpty() ? 0 : Bukkit.getWorlds().get(0).getFullTime() / 24000L;
            start(day);
        }
    }

    public void forceStop() {
        if (active) {
            end();
        }
    }

    private void start(long day) {
        active = true;
        lastTriggerDay = day;
        Title title = Title.title(
                plugin.getMessages().parse(config.announceStart()),
                plugin.getMessages().parse(config.announceStartSub()),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(800)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.playSound(p, Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.6f);
        }
        pressureTaskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::pressure, 40L, config.bmSpawnInterval() * 20L).getTaskId();
        if (config.bmBreakEnabled()) {
            breakTaskId = plugin.getServer().getScheduler()
                    .runTaskTimer(plugin, this::breakBlocks, 60L, config.bmBreakInterval() * 20L).getTaskId();
        }
    }

    private void end() {
        active = false;
        stopTasks();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(plugin.getMessages().parse(config.announceEnd()));
            p.playSound(p, Sound.BLOCK_BELL_USE, 0.8f, 1.2f);
            PlayerProfile profile = plugin.getProfileManager().get(p.getUniqueId());
            if (profile != null) {
                plugin.getClassManager().grantXp(p, profile, config.bloodMoonRewardXp(), "lua de sangue");
            }
        }
    }

    private void stopTasks() {
        cancel(pressureTaskId);
        cancel(breakTaskId);
        pressureTaskId = -1;
        breakTaskId = -1;
    }

    private void cancel(int id) {
        if (id != -1) {
            plugin.getServer().getScheduler().cancelTask(id);
        }
    }

    /** Aplica os buffs da Lua de Sangue a um zumbi que spawnou. */
    public void buff(LivingEntity entity) {
        if (!plugin.getInfectionManager().isZombieType(entity)) {
            return;
        }
        entity.getPersistentDataContainer().set(EntityKeys.BLOOD_MOON_MOB, PersistentDataType.BYTE, (byte) 1);
        var speed = entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * config.bmSpeedMult());
        }
        var health = entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * config.bmHealthMult());
            entity.setHealth(health.getBaseValue());
        }
    }

    private void pressure() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            GameMode mode = player.getGameMode();
            if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
                continue;
            }
            if (countNearbyZombies(player) >= config.bmCapPerPlayer()) {
                continue;
            }
            for (int i = 0; i < config.bmExtraPerPlayer(); i++) {
                Location loc = findSpawnLocation(player, rng);
                if (loc != null) {
                    world.spawnEntity(loc, EntityType.ZOMBIE, CreatureSpawnEvent.SpawnReason.CUSTOM);
                }
            }
        }
    }

    private long countNearbyZombies(Player player) {
        int r = config.bmRadius();
        return player.getNearbyEntities(r, r, r).stream()
                .filter(e -> plugin.getInfectionManager().isZombieType(e)).count();
    }

    private Location findSpawnLocation(Player player, ThreadLocalRandom rng) {
        World world = player.getWorld();
        double angle = rng.nextDouble(Math.PI * 2);
        double dist = config.bmMinRadius() + rng.nextDouble(config.bmRadius() - config.bmMinRadius());
        int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * dist);
        int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        int y = world.getHighestBlockYAt(x, z);
        Block ground = world.getBlockAt(x, y, z);
        Block above = world.getBlockAt(x, y + 1, z);
        Block above2 = world.getBlockAt(x, y + 2, z);
        if (!ground.getType().isSolid() || ground.isLiquid() || !above.getType().isAir() || !above2.getType().isAir()) {
            return null;
        }
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    private void breakBlocks() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int r = config.bmBreakRadius();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            int handled = 0;
            for (var entity : player.getNearbyEntities(r, r, r)) {
                if (handled >= 8) {
                    break;
                }
                if (!plugin.getInfectionManager().isZombieType(entity)) {
                    continue;
                }
                handled++;
                if (rng.nextDouble() >= config.bmBreakChance()) {
                    continue;
                }
                breakWeakBlockNear((LivingEntity) entity);
            }
        }
    }

    private void breakWeakBlockNear(LivingEntity zombie) {
        Location base = zombie.getLocation();
        int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int dy = 0; dy <= 1; dy++) {
            for (int[] off : offsets) {
                Block block = base.clone().add(off[0], dy, off[2]).getBlock();
                if (config.bmBreakBlocks().contains(block.getType())) {
                    block.breakNaturally();
                    block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOOD_BREAK, 1f, 0.8f);
                    return;
                }
            }
        }
    }
}
