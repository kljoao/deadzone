package com.deadzone.modules.clan;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Símbolo de aliado acima da cabeça: um TextDisplay montado no jogador, visível SÓ para os
 * companheiros de clan (escondido dos demais via hideEntity, então não conflita com a nametag do TAB).
 * Respeita o toggle {@code symbol} do clan. Reconciliado periodicamente; varre órfãos no boot.
 */
public class ClanSymbolService implements Listener {

    private static final String SYMBOL = "⛨";
    private static final long PERIOD = 20L; // 1s
    private static final float HEIGHT = 1.0f; // deslocamento acima do ponto de montagem (ajustável)

    private final DeadzonePlugin plugin;
    private final ClanManager clans;
    private final NamespacedKey markerKey;

    private final Map<UUID, Marker> markers = new HashMap<>(); // dono -> marcador
    private int taskId = -1;

    public ClanSymbolService(DeadzonePlugin plugin, ClanManager clans) {
        this.plugin = plugin;
        this.clans = clans;
        this.markerKey = new NamespacedKey(plugin, "clan_symbol");
    }

    public void enable() {
        sweepOrphans();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.taskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::reconcile, 40L, PERIOD).getTaskId();
    }

    public void disable() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        for (Marker m : markers.values()) {
            if (m.display != null && !m.display.isDead()) {
                m.display.remove();
            }
        }
        markers.clear();
    }

    /** Esconde marcadores existentes de quem acabou de entrar (a menos que seja aliado). */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
        for (Marker m : markers.values()) {
            if (m.display == null || m.display.isDead()) {
                continue;
            }
            if (!clans.areClanmates(joiner.getUniqueId(), m.owner)) {
                joiner.hideEntity(plugin, m.display);
            }
        }
    }

    private void reconcile() {
        // remove marcadores de quem saiu ou não deve mais ter
        markers.entrySet().removeIf(e -> {
            Player owner = plugin.getServer().getPlayer(e.getKey());
            Clan clan = owner != null ? clans.getClanOf(owner.getUniqueId()) : null;
            boolean eligible = owner != null && owner.isOnline() && clan != null && clan.symbol();
            if (!eligible) {
                if (e.getValue().display != null && !e.getValue().display.isDead()) {
                    e.getValue().display.remove();
                }
                return true;
            }
            return false;
        });

        for (Player owner : plugin.getServer().getOnlinePlayers()) {
            Clan clan = clans.getClanOf(owner.getUniqueId());
            if (clan == null || !clan.symbol()) {
                continue;
            }
            Marker marker = markers.get(owner.getUniqueId());
            // Respawn se: não existe, morreu, OU ficou órfão em outro mundo (troca de mundo/portal).
            boolean orphaned = marker != null && marker.display != null && !marker.display.isDead()
                    && !marker.display.getWorld().equals(owner.getWorld());
            if (marker == null || marker.display == null || marker.display.isDead() || orphaned) {
                if (orphaned) {
                    marker.display.remove(); // remove o display preso no mundo antigo
                }
                marker = spawn(owner, clan);
                markers.put(owner.getUniqueId(), marker);
            } else {
                // garante que continua montado e com a cor certa
                if (!owner.getPassengers().contains(marker.display)) {
                    owner.addPassenger(marker.display);
                }
                marker.display.text(Component.text(SYMBOL, clan.namedColor()));
            }
            updateVisibility(owner, marker);
        }
    }

    private Marker spawn(Player owner, Clan clan) {
        World world = owner.getWorld();
        TextDisplay display = world.spawn(owner.getLocation(), TextDisplay.class, d -> {
            d.text(Component.text(SYMBOL, clan.namedColor()));
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(true);
            d.setShadowed(false);
            d.setDefaultBackground(false);
            d.setViewRange(1.0f);
            d.setTransformation(new Transformation(
                    new Vector3f(0f, HEIGHT, 0f), new Quaternionf(),
                    new Vector3f(1f, 1f, 1f), new Quaternionf()));
            d.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        });
        owner.addPassenger(display);
        // por padrão é visível a todos: esconde de quem não é aliado já na criação
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (!clans.areClanmates(viewer.getUniqueId(), owner.getUniqueId())) {
                viewer.hideEntity(plugin, display);
            }
        }
        Marker marker = new Marker(owner.getUniqueId(), display);
        return marker;
    }

    private void updateVisibility(Player owner, Marker marker) {
        Set<UUID> desired = new HashSet<>();
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (clans.areClanmates(viewer.getUniqueId(), owner.getUniqueId())) {
                desired.add(viewer.getUniqueId());
            }
        }
        // mostra novos aliados
        for (UUID id : desired) {
            if (!marker.shownTo.contains(id)) {
                Player viewer = plugin.getServer().getPlayer(id);
                if (viewer != null) {
                    viewer.showEntity(plugin, marker.display);
                }
            }
        }
        // esconde quem deixou de ser aliado
        for (UUID id : marker.shownTo) {
            if (!desired.contains(id)) {
                Player viewer = plugin.getServer().getPlayer(id);
                if (viewer != null) {
                    viewer.hideEntity(plugin, marker.display);
                }
            }
        }
        marker.shownTo.clear();
        marker.shownTo.addAll(desired);
    }

    private void sweepOrphans() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e instanceof TextDisplay
                        && e.getPersistentDataContainer().has(markerKey, PersistentDataType.STRING)) {
                    e.remove();
                }
            }
        }
    }

    private static final class Marker {
        final UUID owner;
        final TextDisplay display;
        final Set<UUID> shownTo = new HashSet<>();

        Marker(UUID owner, TextDisplay display) {
            this.owner = owner;
            this.display = display;
        }
    }
}
