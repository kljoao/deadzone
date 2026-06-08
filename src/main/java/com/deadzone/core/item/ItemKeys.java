package com.deadzone.core.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * NamespacedKeys centralizados usados nos PersistentDataContainers.
 * Deve ser inicializado no onEnable antes de qualquer uso.
 */
public final class ItemKeys {

    /** Identificador do item customizado (ex.: "bandagem"). */
    public static NamespacedKey ITEM_ID;

    private ItemKeys() {
    }

    public static void init(Plugin plugin) {
        ITEM_ID = new NamespacedKey(plugin, "item_id");
    }
}
