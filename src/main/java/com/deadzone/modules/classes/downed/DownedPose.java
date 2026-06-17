package com.deadzone.modules.classes.downed;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * "Deita" o jogador derrubado usando a pose de NADAR (rastejando) via ProtocolLib.
 * Vira no-op se o ProtocolLib não estiver presente.
 */
public final class DownedPose {

    private static final boolean AVAILABLE = Bukkit.getPluginManager().isPluginEnabled("ProtocolLib");
    // Índice da metadata "flags compartilhadas" da entidade (byte; bit 0x10 = nadando).
    private static final int FLAGS_INDEX = 0;
    private static final byte FLAG_SWIMMING = 0x10;
    // Índice da metadata "pose" da entidade (estável em 1.20.5–1.21.x).
    private static final int POSE_INDEX = 6;

    private DownedPose() {
    }

    public static boolean available() {
        return AVAILABLE;
    }

    /** Faz o jogador parecer deitado (flag de natação + pose nadando). */
    public static void lieDown(Player player) {
        send(player, EnumWrappers.EntityPose.SWIMMING, FLAG_SWIMMING);
    }

    /** Volta o jogador a ficar em pé (limpa o flag de natação + pose em pé). */
    public static void standUp(Player player) {
        send(player, EnumWrappers.EntityPose.STANDING, (byte) 0x00);
    }

    private static void send(Player player, EnumWrappers.EntityPose pose, byte flags) {
        if (!AVAILABLE || !player.isOnline()) {
            return;
        }
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().write(0, player.getEntityId());

        // Enviamos flags (byte) + pose juntos: o cliente renderiza o "rastejar" de forma fiel,
        // e como não tocamos no swimming server-side, o servidor não fica resetando isto.
        WrappedDataWatcher.Serializer byteSerializer = WrappedDataWatcher.Registry.get(Byte.class);
        WrappedDataWatcher.Serializer poseSerializer =
                WrappedDataWatcher.Registry.get(EnumWrappers.getEntityPoseClass());
        Object nmsPose = EnumWrappers.getEntityPoseConverter().getGeneric(pose);

        packet.getDataValueCollectionModifier().write(0, List.of(
                new WrappedDataValue(FLAGS_INDEX, byteSerializer, flags),
                new WrappedDataValue(POSE_INDEX, poseSerializer, nmsPose)));

        for (Player viewer : player.getWorld().getPlayers()) {
            if (viewer.equals(player) || viewer.canSee(player)) {
                pm.sendServerPacket(viewer, packet);
            }
        }
    }
}
