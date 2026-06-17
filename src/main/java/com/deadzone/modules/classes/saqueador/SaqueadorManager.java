package com.deadzone.modules.classes.saqueador;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.entity.EntityKeys;
import com.deadzone.core.profile.PlayerClass;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.modules.classes.ClassConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Skills do Saqueador: Sexto Sentido (Rádio) e lockpicking (Pé de Cabra).
 */
public class SaqueadorManager {

    public static final String SKILL_SIXTH_SENSE = "saq_sexto_sentido";
    public static final String SKILL_LOCKPICK = "saq_lockpicking";

    private final DeadzonePlugin plugin;
    private final ClassConfig config;
    private final Map<UUID, Long> radioCooldown = new HashMap<>();

    public SaqueadorManager(DeadzonePlugin plugin, ClassConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void usePing(Player player) {
        PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
        if (profile == null || profile.getPlayerClass() != PlayerClass.SAQUEADOR || !profile.hasSkill(SKILL_SIXTH_SENSE)) {
            player.sendActionBar(Component.text("Você não possui o Sexto Sentido.", NamedTextColor.RED));
            return;
        }
        long remaining = cooldownRemaining(player.getUniqueId());
        if (remaining > 0) {
            player.sendActionBar(Component.text("Rádio recarregando (" + (remaining / 60 + 1) + " min).", NamedTextColor.GRAY));
            return;
        }
        radioCooldown.put(player.getUniqueId(), System.currentTimeMillis() + config.radioCooldownSeconds() * 1000L);

        List<Location> spots = scanContainers(player);
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
        if (spots.isEmpty()) {
            player.sendActionBar(Component.text("Nenhum loot por perto.", NamedTextColor.GRAY));
            return;
        }
        player.sendActionBar(Component.text(spots.size() + " baús com loot revelados!", NamedTextColor.GREEN));
        highlight(player, spots);
    }

    private long cooldownRemaining(UUID uuid) {
        Long until = radioCooldown.get(uuid);
        if (until == null) {
            return 0;
        }
        long ms = until - System.currentTimeMillis();
        return ms <= 0 ? 0 : ms / 1000;
    }

    private List<Location> scanContainers(Player player) {
        List<Location> spots = new ArrayList<>();
        World world = player.getWorld();
        Location origin = player.getLocation();
        int radius = config.radioRadius();
        int chunkRadius = (radius >> 4) + 1;
        Chunk center = origin.getChunk();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int cx = center.getX() + dx;
                int cz = center.getZ() + dz;
                // Só chunks JÁ carregados: não força carregamento/geração (stall) ao usar o rádio.
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                Chunk chunk = world.getChunkAt(cx, cz);
                for (BlockState state : chunk.getTileEntities()) {
                    if (!(state instanceof Container container)) {
                        continue;
                    }
                    Block block = state.getBlock();
                    if (!config.radioContainers().contains(block.getType())) {
                        continue;
                    }
                    if (block.getLocation().distanceSquared(origin) > (double) radius * radius) {
                        continue;
                    }
                    if (!container.getInventory().isEmpty()) {
                        spots.add(block.getLocation().add(0.5, 1.0, 0.5));
                    }
                }
            }
        }
        return spots;
    }

    private void highlight(Player player, List<Location> spots) {
        int durationTicks = config.radioDurationSeconds() * 20;
        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= durationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                for (Location loc : spots) {
                    player.spawnParticle(Particle.HAPPY_VILLAGER, loc, 6, 0.25, 0.4, 0.25, 0);
                }
                elapsed += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    public boolean isLocked(Block block) {
        if (block.getState() instanceof TileState ts) {
            Byte v = ts.getPersistentDataContainer().get(EntityKeys.LOCKED_CONTAINER, PersistentDataType.BYTE);
            return v != null && v == 1;
        }
        return false;
    }

    public boolean setLocked(Block block, boolean locked) {
        if (!(block.getState() instanceof TileState ts)) {
            return false;
        }
        if (locked) {
            ts.getPersistentDataContainer().set(EntityKeys.LOCKED_CONTAINER, PersistentDataType.BYTE, (byte) 1);
        } else {
            ts.getPersistentDataContainer().remove(EntityKeys.LOCKED_CONTAINER);
        }
        ts.update();
        return true;
    }

    public boolean canPick(Player player) {
        PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
        return profile != null && profile.getPlayerClass() == PlayerClass.SAQUEADOR && profile.hasSkill(SKILL_LOCKPICK);
    }
}
