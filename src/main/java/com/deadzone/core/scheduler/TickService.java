package com.deadzone.core.scheduler;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.core.profile.ProfileManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Loop temporal central: módulos registram handlers aqui e iteramos os jogadores
 * online uma única vez por ciclo, em vez de cada um criar seu próprio BukkitRunnable.
 */
public class TickService {

    private final DeadzonePlugin plugin;
    private final ProfileManager profiles;

    private final List<Consumer<PlayerProfile>> secondHandlers = new ArrayList<>();

    private int secondTaskId = -1;

    public TickService(DeadzonePlugin plugin, ProfileManager profiles) {
        this.plugin = plugin;
        this.profiles = profiles;
    }

    /** Registra um handler executado 1x por segundo para cada jogador online com perfil carregado. */
    public void registerSecondHandler(Consumer<PlayerProfile> handler) {
        secondHandlers.add(handler);
    }

    public void start() {
        this.secondTaskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tickSecond, 20L, 20L).getTaskId();
    }

    public void stop() {
        if (secondTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(secondTaskId);
            secondTaskId = -1;
        }
    }

    private void tickSecond() {
        if (secondHandlers.isEmpty()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerProfile profile = profiles.get(player.getUniqueId());
            if (profile == null) {
                continue;
            }
            for (Consumer<PlayerProfile> handler : secondHandlers) {
                try {
                    handler.accept(profile);
                } catch (Exception e) {
                    plugin.getLogger().warning("Erro em tick handler: " + e.getMessage());
                }
            }
        }
    }
}
