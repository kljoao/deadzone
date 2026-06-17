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

    /** Munição atual no pente de uma arma de fogo (int). */
    public static NamespacedKey FIREARM_AMMO;

    /** Modo de disparo selecionado de uma arma ("SEMI"/"AUTO"). */
    public static NamespacedKey FIREARM_MODE;

    /** CMD base da skin aplicada na arma (substitui a CMD padrão como "base" do modelo). */
    public static NamespacedKey FIREARM_SKIN_CMD;

    /** Id da skin aplicada (cosmético), para exibição/persistência. */
    public static NamespacedKey FIREARM_SKIN_ID;

    private ItemKeys() {
    }

    public static void init(Plugin plugin) {
        ITEM_ID = new NamespacedKey(plugin, "item_id");
        FIREARM_AMMO = new NamespacedKey(plugin, "firearm_ammo");
        FIREARM_MODE = new NamespacedKey(plugin, "firearm_mode");
        FIREARM_SKIN_CMD = new NamespacedKey(plugin, "firearm_skin_cmd");
        FIREARM_SKIN_ID = new NamespacedKey(plugin, "firearm_skin_id");
    }
}
