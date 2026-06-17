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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Cache em memória dos perfis + persistência assíncrona.
 * Load no join, save no quit, autosave periódico, wipe na morte e flush síncrono no shutdown.
 */
public class ProfileManager {

    private final DeadzonePlugin plugin;
    private final PlayerProfileDao dao;
    private final ConfigManager config;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    private final List<BiConsumer<Player, PlayerProfile>> loadHooks = new ArrayList<>();

    // Todas as operações de DB passam por UMA thread (FIFO): garante que o save do quit
    // termine antes do load do rejoin (sem race de relog rápido) e que não se sobreponham.
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Deadzone-DB");
        t.setDaemon(true);
        return t;
    });

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
        // Drena os saves pendentes na fila antes do flush final (síncrono).
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Saves de perfil pendentes não terminaram a tempo.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        saveAllSync();
    }

    /** Callback executado (na main thread) após carregar o perfil no join. */
    public void onProfileLoaded(BiConsumer<Player, PlayerProfile> hook) {
        loadHooks.add(hook);
    }

    /**
     * Roda uma tarefa de banco na MESMA thread FIFO dos load/save de perfil. Usado pela economia
     * para mexer no saldo de jogadores OFFLINE sem corrida com o carregamento no login.
     */
    public void submitDbTask(Runnable task) {
        dbExecutor.execute(task);
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
        dbExecutor.execute(() -> {
            PlayerProfile loaded;
            try {
                loaded = dao.load(id);
            } catch (Exception e) {
                plugin.getLogger().severe("Falha ao carregar perfil de " + name + ": " + e.getMessage());
                loaded = null;
            }
            final PlayerProfile profile = (loaded != null) ? loaded : PlayerProfile.createDefault(id, name);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player online = plugin.getServer().getPlayer(id);
                if (online == null) {
                    return; // saiu antes do load terminar: não cacheia perfil-fantasma p/ offline
                }
                profile.setLastKnownName(name); // mutação só na main thread
                cache.put(id, profile);
                for (BiConsumer<Player, PlayerProfile> hook : loadHooks) {
                    hook.accept(online, profile);
                }
            });
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
        List<PlayerProfile> dirtyProfiles = new ArrayList<>();
        List<ProfileSnapshot> snaps = new ArrayList<>();
        for (PlayerProfile p : cache.values()) {
            if (p.isDirty()) {
                dirtyProfiles.add(p);
                snaps.add(p.snapshot());
                p.setDirty(false);
            }
        }
        if (snaps.isEmpty()) {
            return;
        }
        dbExecutor.execute(() -> {
            try {
                dao.saveAll(snaps);
            } catch (Exception e) {
                plugin.getLogger().warning("Autosave falhou (re-marcando p/ retry): " + e.getMessage());
                // Save falhou: re-marca como sujo (na main thread) para tentar de novo, sem perder dados.
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> dirtyProfiles.forEach(p -> p.setDirty(true)));
            }
        });
    }

    private void saveAsync(ProfileSnapshot snapshot) {
        dbExecutor.execute(() -> {
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
