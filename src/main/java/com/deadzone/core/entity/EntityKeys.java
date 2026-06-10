package com.deadzone.core.entity;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** NamespacedKeys usados no PDC de entidades (zumbis mutantes, containers, etc.). */
public final class EntityKeys {

    /** Tipo de zumbi mutante: "RUNNER", "TANK", "EXPLODER". */
    public static NamespacedKey ZOMBIE_TYPE;

    /** Marca um container (baú/barril) como trancado (PDC do TileState). */
    public static NamespacedKey LOCKED_CONTAINER;

    /** Marca um zumbi spawnado durante a Lua de Sangue. */
    public static NamespacedKey BLOOD_MOON_MOB;

    /** Loot guardado dentro de um zumbi que era um jogador (byte array serializado). */
    public static NamespacedKey PLAYER_ZOMBIE_LOOT;

    /** Marca um zumbi de alucinação (ignorado por mutação/buffs/loot). */
    public static NamespacedKey HALLUCINATION;

    /** Marca um projétil (bola de neve) disparado por arma de fogo; valor = dano. */
    public static NamespacedKey FIREARM_BULLET;

    private EntityKeys() {
    }

    public static void init(Plugin plugin) {
        ZOMBIE_TYPE = new NamespacedKey(plugin, "zombie_type");
        LOCKED_CONTAINER = new NamespacedKey(plugin, "locked_container");
        BLOOD_MOON_MOB = new NamespacedKey(plugin, "blood_moon_mob");
        PLAYER_ZOMBIE_LOOT = new NamespacedKey(plugin, "player_zombie_loot");
        HALLUCINATION = new NamespacedKey(plugin, "hallucination");
        FIREARM_BULLET = new NamespacedKey(plugin, "firearm_bullet");
    }
}
