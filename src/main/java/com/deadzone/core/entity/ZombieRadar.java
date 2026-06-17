package com.deadzone.core.entity;

import com.deadzone.DeadzonePlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cache compartilhado de "zumbis perto". Sanidade e Atmosfera precisavam disso a cada segundo,
 * cada um com seu próprio {@code getNearbyEntities} (2 varreduras/player/seg). Aqui fazemos UMA
 * varredura por player a cada ~segundo (no maior raio pedido) e cada consumidor conta dentro do
 * seu raio filtrando o snapshot — barato e sem sqrt.
 */
public final class ZombieRadar implements Listener {

    private static final long TTL_MS = 900; // dentro do mesmo "segundo" os dois consumidores reaproveitam

    private final DeadzonePlugin plugin;
    private final Map<UUID, Snapshot> cache = new HashMap<>();
    private int scanRadius = 16;

    public ZombieRadar(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Garante que a varredura cubra pelo menos este raio (chamado pelos consumidores no enable). */
    public void ensureRadius(int radius) {
        if (radius > scanRadius) {
            scanRadius = radius;
        }
    }

    /** Quantos zumbis dentro de {@code radius} do jogador (reusa o snapshot do segundo atual). */
    public int count(Player player, int radius) {
        List<Entity> zombies = snapshot(player);
        if (radius >= scanRadius) {
            return zombies.size();
        }
        Location pl = player.getLocation();
        int n = 0;
        for (Entity e : zombies) {
            Location el = e.getLocation();
            if (Math.abs(el.getX() - pl.getX()) <= radius
                    && Math.abs(el.getY() - pl.getY()) <= radius
                    && Math.abs(el.getZ() - pl.getZ()) <= radius) {
                n++;
            }
        }
        return n;
    }

    private List<Entity> snapshot(Player player) {
        long now = System.currentTimeMillis();
        Snapshot s = cache.get(player.getUniqueId());
        if (s != null && now - s.at < TTL_MS) {
            return s.zombies;
        }
        List<Entity> zombies = new ArrayList<>();
        for (Entity e : player.getNearbyEntities(scanRadius, scanRadius, scanRadius)) {
            if (plugin.getInfectionManager().isZombieType(e)) {
                zombies.add(e);
            }
        }
        cache.put(player.getUniqueId(), new Snapshot(now, zombies));
        return zombies;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    private static final class Snapshot {
        final long at;
        final List<Entity> zombies;

        Snapshot(long at, List<Entity> zombies) {
            this.at = at;
            this.zombies = zombies;
        }
    }
}
