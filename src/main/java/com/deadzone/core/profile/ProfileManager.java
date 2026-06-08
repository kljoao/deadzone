package com.deadzone.core.profile;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.database.dao.PlayerProfileDao;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache em memória dos perfis + persistência assíncrona.
 * Load no join, save no quit, autosave periódico, wipe na morte e flush síncrono no shutdown.
 */
public class ProfileManager {

    private final DeadzonePlugin plugin;
    private final PlayerProfileDao dao;
    private final ConfigManager config;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    private int autosaveTaskId = -1;

    public ProfileManager(DeadzonePlugin plugin, PlayerProfileDao dao, ConfigManager config) {
        this.plugin = plugin;
        this.dao = dao;
        this.config = config;
    }

    public void init() {
        plugin.getServer().getPluginManager().registerEvents(new ProfileListener(this), plugin);

        long periodTicks = config.autosaveMinutes() * 60L * 20L;
        this.autosaveTaskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::autosave, periodTicks, periodTicks).getTaskId();

        // Em caso de /reload, carrega quem já está online.
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            handleJoin(online);
        }
    }

    public void shutdown() {
        if (autosaveTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(autosaveTaskId);
            autosaveTaskId = -1;
        }
        saveAllSync();
    }

    public PlayerProfile get(UUID uuid) {
        return cache.get(uuid);
    }

    public PlayerProfile get(Player player) {
        return cache.get(player.getUniqueId());
    }

    public Collection<PlayerProfile> cached() {
        return cache.values();
    }

    public void handleJoin(Player player) {
        UUID id = player.getUniqueId();
        String name = player.getName();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerProfile loaded;
            try {
                loaded = dao.load(id);
            } catch (Exception e) {
                plugin.getLogger().severe("Falha ao carregar perfil de " + name + ": " + e.getMessage());
                loaded = null;
            }
            final PlayerProfile profile = (loaded != null) ? loaded : PlayerProfile.createDefault(id, name);
            profile.setLastKnownName(name);
            plugin.getServer().getScheduler().runTask(plugin, () -> cache.put(id, profile));
        });
    }

    public void handleQuit(Player player) {
        PlayerProfile profile = cache.remove(player.getUniqueId());
        if (profile == null) {
            return;
        }
        profile.setLastSeen(System.currentTimeMillis());
        saveAsync(profile.snapshot());
    }

    public void handleDeath(Player player) {
        if (!config.wipeEnabled()) {
            return;
        }
        PlayerProfile profile = cache.get(player.getUniqueId());
        if (profile == null) {
            return;
        }
        profile.resetToDefaults(config.wipeResetXp(), config.keepTotalXpStat());
        saveAsync(profile.snapshot());
        if (config.debug()) {
            plugin.getLogger().info("Wipe total aplicado a " + player.getName() + " (morte).");
        }
    }

    private void autosave() {
        List<ProfileSnapshot> snaps = new ArrayList<>();
        for (PlayerProfile p : cache.values()) {
            if (p.isDirty()) {
                snaps.add(p.snapshot());
                p.setDirty(false);
            }
        }
        if (snaps.isEmpty()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                dao.saveAll(snaps);
            } catch (Exception e) {
                plugin.getLogger().warning("Autosave falhou: " + e.getMessage());
            }
        });
    }

    private void saveAsync(ProfileSnapshot snapshot) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                dao.save(snapshot);
            } catch (Exception e) {
                plugin.getLogger().warning("Save assíncrono falhou: " + e.getMessage());
            }
        });
    }

    /** Flush SÍNCRONO de todos os perfis (chamado no onDisable). */
    public void saveAllSync() {
        if (cache.isEmpty()) {
            return;
        }
        List<ProfileSnapshot> snaps = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (PlayerProfile p : cache.values()) {
            p.setLastSeen(now);
            snaps.add(p.snapshot());
        }
        try {
            dao.saveAll(snaps);
        } catch (Exception e) {
            plugin.getLogger().severe("Flush síncrono de perfis falhou: " + e.getMessage());
        }
        cache.clear();
    }
}
