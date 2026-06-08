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

    private EntityKeys() {
    }

    public static void init(Plugin plugin) {
        ZOMBIE_TYPE = new NamespacedKey(plugin, "zombie_type");
        LOCKED_CONTAINER = new NamespacedKey(plugin, "locked_container");
        BLOOD_MOON_MOB = new NamespacedKey(plugin, "blood_moon_mob");
    }
}
