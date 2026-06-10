package com.deadzone.modules.atmosphere;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.entity.EntityKeys;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.core.scheduler.TickService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sons ambiente que sobem de tensão conforme a ameaça, e alucinações (zumbis falsos,
 * sussurros) quando a sanidade está baixa — tudo visível/audível só para o jogador.
 */
public class AtmosphereManager {

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;
    private final Map<UUID, Long> lastAmbient = new HashMap<>();

    private boolean ambientEnabled;
    private double ambientBaseChance;
    private double ambientTenseChance;
    private long ambientMinIntervalMs;
    private int threatRadius;
    private boolean halluEnabled;
    private int halluBelow;
    private double halluChance;
    private int fakeZombieSeconds;

    public AtmosphereManager(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    public void enable(TickService tickService) {
        tickService.registerSecondHandler(this::tick);
    }

    public void reload() {
        load();
    }

    private void load() {
        FileConfiguration c = configManager.loadConfig("atmosphere.yml");
        this.ambientEnabled = c.getBoolean("ambient.enabled", true);
        this.ambientBaseChance = c.getDouble("ambient.base-chance", 0.04);
        this.ambientTenseChance = c.getDouble("ambient.tense-chance", 0.3);
        this.ambientMinIntervalMs = c.getLong("ambient.min-interval-seconds", 14) * 1000L;
        this.threatRadius = c.getInt("ambient.threat-radius", 16);
        this.halluEnabled = c.getBoolean("hallucinations.enabled", true);
        this.halluBelow = c.getInt("hallucinations.below", 30);
        this.halluChance = c.getDouble("hallucinations.chance-per-second", 0.08);
        this.fakeZombieSeconds = c.getInt("hallucinations.fake-zombie-seconds", 4);
    }

    private void tick(PlayerProfile profile) {
        Player player = plugin.getServer().getPlayer(profile.getUuid());
        if (player == null) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
            return;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int threat = countZombies(player);

        if (ambientEnabled) {
            playAmbient(player, threat, profile.getSanity(), rng);
        }
        if (halluEnabled && profile.getSanity() < halluBelow && rng.nextDouble() < halluChance) {
            hallucinate(player, rng);
        }
    }

    private void playAmbient(Player player, int threat, double sanity, ThreadLocalRandom rng) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now < lastAmbient.getOrDefault(uuid, 0L) + ambientMinIntervalMs) {
            return; // respeita um intervalo mínimo entre sons (evita spam)
        }
        boolean tense = threat >= 4 || sanity < 40;
        if (tense) {
            if (rng.nextDouble() < ambientTenseChance) {
                player.playSound(player, Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, 0.7f);
                lastAmbient.put(uuid, now);
            }
        } else if (rng.nextDouble() < ambientBaseChance) {
            player.playSound(offset(player, rng, 10), Sound.AMBIENT_CAVE, 0.7f, 0.8f);
            lastAmbient.put(uuid, now);
        }
    }

    private void hallucinate(Player player, ThreadLocalRandom rng) {
        switch (rng.nextInt(3)) {
            case 0 -> player.playSound(offset(player, rng, 4), Sound.ENTITY_ZOMBIE_AMBIENT, 0.9f, 0.6f);
            case 1 -> {
                player.playSound(player, Sound.ENTITY_ENDERMAN_STARE, 0.8f, 0.8f);
                player.playSound(offset(player, rng, 12), Sound.ENTITY_GHAST_AMBIENT, 0.4f, 0.5f);
            }
            default -> spawnFakeZombie(player, rng);
        }
    }

    private void spawnFakeZombie(Player player, ThreadLocalRandom rng) {
        Location loc = groundNear(player, rng);
        if (loc == null) {
            player.playSound(offset(player, rng, 4), Sound.ENTITY_ZOMBIE_AMBIENT, 0.9f, 0.6f);
            return;
        }
        Zombie zombie = player.getWorld().spawn(loc, Zombie.class, z -> {
            z.getPersistentDataContainer().set(EntityKeys.HALLUCINATION, PersistentDataType.BYTE, (byte) 1);
            z.setAI(false);
            z.setInvulnerable(true);
            z.setSilent(true);
            z.setCollidable(false);
            z.setPersistent(false);
            z.setVisibleByDefault(false);
        });
        player.showEntity(plugin, zombie);
        player.playSound(player, Sound.ENTITY_ZOMBIE_AMBIENT, 0.8f, 0.7f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (zombie.isValid()) {
                zombie.remove();
            }
        }, fakeZombieSeconds * 20L);
    }

    private Location groundNear(Player player, ThreadLocalRandom rng) {
        for (int i = 0; i < 6; i++) {
            double angle = rng.nextDouble(Math.PI * 2);
            double dist = 3 + rng.nextDouble(5);
            int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = player.getLocation().getBlockY();
            Block feet = player.getWorld().getBlockAt(x, y, z);
            Block below = player.getWorld().getBlockAt(x, y - 1, z);
            Block head = player.getWorld().getBlockAt(x, y + 1, z);
            if (below.getType().isSolid() && feet.getType().isAir() && head.getType().isAir()) {
                return new Location(player.getWorld(), x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    private Location offset(Player player, ThreadLocalRandom rng, double radius) {
        double angle = rng.nextDouble(Math.PI * 2);
        return player.getLocation().add(Math.cos(angle) * radius, rng.nextDouble(-2, 3), Math.sin(angle) * radius);
    }

    private int countZombies(Player player) {
        int count = 0;
        for (var entity : player.getNearbyEntities(threatRadius, threatRadius, threatRadius)) {
            if (plugin.getInfectionManager().isZombieType(entity)) {
                count++;
            }
        }
        return count;
    }
}
