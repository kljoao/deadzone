package com.deadzone.modules.clan;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.deadzone.DeadzonePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Glow seletivo de aliados: membros do mesmo clan se veem com contorno (mesmo através de paredes)
 * dentro do alcance, e SÓ eles veem (inimigos não recebem o pacote). Implementado por pacotes de
 * metadata per-viewer (ProtocolLib): a flag 0x40 é injetada só para os viewers aliados.
 *
 * Re-afirma a cada tick-interval porque o servidor envia metadata "real" (sem glow) ao mexer em
 * pose/sneak/sprint; reenviar mantém o contorno estável. Vira no-op sem ProtocolLib.
 */
public class ClanGlowService {

    private static final boolean AVAILABLE = Bukkit.getPluginManager().isPluginEnabled("ProtocolLib");
    private static final int FLAGS_INDEX = 0;
    private static final byte GLOW = 0x40;
    private static final double RANGE = 50.0;
    private static final double RANGE_SQ = RANGE * RANGE;
    private static final long PERIOD = 10L; // a cada 0.5s

    private final DeadzonePlugin plugin;
    private final ClanManager clans;

    // alvo -> viewers que atualmente recebem o glow dele
    private final Map<UUID, Set<UUID>> active = new HashMap<>();
    private int taskId = -1;

    public ClanGlowService(DeadzonePlugin plugin, ClanManager clans) {
        this.plugin = plugin;
        this.clans = clans;
    }

    public void enable() {
        if (!AVAILABLE) {
            plugin.getLogger().warning("ProtocolLib ausente: glow de aliados desativado.");
            return;
        }
        this.taskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, 40L, PERIOD).getTaskId();
    }

    public void disable() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        // apaga todo glow ativo
        for (Map.Entry<UUID, Set<UUID>> e : active.entrySet()) {
            Player target = Bukkit.getPlayer(e.getKey());
            if (target == null) {
                continue;
            }
            for (UUID viewerId : e.getValue()) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null) {
                    sendGlow(target, viewer, false);
                }
            }
        }
        active.clear();
    }

    private void tick() {
        Map<UUID, Set<UUID>> desired = computeDesired();

        // apaga pares que saíram (estavam ativos, não estão mais no desejado)
        for (Map.Entry<UUID, Set<UUID>> e : active.entrySet()) {
            Player target = Bukkit.getPlayer(e.getKey());
            Set<UUID> now = desired.getOrDefault(e.getKey(), Set.of());
            for (UUID viewerId : e.getValue()) {
                if (!now.contains(viewerId) && target != null) {
                    Player viewer = Bukkit.getPlayer(viewerId);
                    if (viewer != null) {
                        sendGlow(target, viewer, false);
                    }
                }
            }
        }

        // (re)afirma o glow para todos os pares desejados
        for (Map.Entry<UUID, Set<UUID>> e : desired.entrySet()) {
            Player target = Bukkit.getPlayer(e.getKey());
            if (target == null) {
                continue;
            }
            for (UUID viewerId : e.getValue()) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null) {
                    sendGlow(target, viewer, true);
                }
            }
        }

        active.clear();
        active.putAll(desired);
    }

    private Map<UUID, Set<UUID>> computeDesired() {
        Map<UUID, Set<UUID>> desired = new HashMap<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            Clan clan = clans.getClanOf(target.getUniqueId());
            if (clan == null || !clan.glow()) {
                continue;
            }
            for (UUID mateId : clan.membersMap().keySet()) {
                if (mateId.equals(target.getUniqueId())) {
                    continue;
                }
                Player viewer = Bukkit.getPlayer(mateId);
                if (viewer == null || !viewer.getWorld().equals(target.getWorld())) {
                    continue;
                }
                if (viewer.getLocation().distanceSquared(target.getLocation()) > RANGE_SQ) {
                    continue;
                }
                desired.computeIfAbsent(target.getUniqueId(), k -> new HashSet<>()).add(mateId);
            }
        }
        return desired;
    }

    private void sendGlow(Player target, Player viewer, boolean on) {
        if (!target.isOnline() || !viewer.isOnline()) {
            return;
        }
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().write(0, target.getEntityId());
        WrappedDataWatcher.Serializer byteSerializer = WrappedDataWatcher.Registry.get(Byte.class);
        byte flags = (byte) (baseFlags(target) | (on ? GLOW : 0));
        packet.getDataValueCollectionModifier().write(0, List.of(
                new WrappedDataValue(FLAGS_INDEX, byteSerializer, flags)));
        pm.sendServerPacket(viewer, packet);
    }

    /** Reconstrói a byte de flags da entidade a partir do estado visível (sem o bit de glow). */
    private byte baseFlags(Player p) {
        byte f = 0;
        if (p.getFireTicks() > 0) {
            f |= 0x01;
        }
        if (p.isSneaking()) {
            f |= 0x02;
        }
        if (p.isSprinting()) {
            f |= 0x08;
        }
        if (p.isInvisible()) {
            f |= 0x20; // preserva invisibilidade (senão o glow re-afirma e "pisca" o aliado visível)
        }
        if (p.isSwimming()) {
            f |= 0x10;
        }
        if (p.isGliding()) {
            f |= (byte) 0x80;
        }
        return f;
    }
}
