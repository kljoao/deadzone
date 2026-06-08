package com.deadzone.core.item;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Base de todos os itens customizados. A identidade vive no PDC (ITEM_ID),
 * não no nome/material — robusto a renomear.
 */
public abstract class CustomItem {

    /** Identificador único e estável (ex.: "bandagem"). */
    public abstract String id();

    /** Cria um novo ItemStack deste item, já com o PDC marcado. */
    public abstract ItemStack build();

    /**
     * Chamado no clique direito com o item na mão.
     * @return true se a interação foi consumida (o evento será cancelado).
     */
    public boolean onUse(Player player, ItemStack stack) {
        return false;
    }

    public Tier tier() {
        return Tier.T1;
    }

    /** Se false, só fabricável por quem tiver {@link #requiredSkillId()} na bancada. */
    public boolean craftableByEveryone() {
        return true;
    }

    public String requiredSkillId() {
        return null;
    }

    /** Helper: grava o ITEM_ID no PDC de um stack já construído. */
    protected void tag(ItemStack stack) {
        stack.editMeta(meta ->
                meta.getPersistentDataContainer().set(ItemKeys.ITEM_ID, PersistentDataType.STRING, id()));
    }
}
