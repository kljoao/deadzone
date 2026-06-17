package com.deadzone.modules.loot;

import org.bukkit.inventory.ItemStack;

/**
 * Conteúdo COMPARTILHADO de um baú (todos os jogadores disputam o mesmo pool).
 * Um slot vira null quando alguém pega o item. Recarrega (nova generation) quando expira o cooldown.
 */
class LootSession {

    final ItemStack[] contents; // null = já pego
    final int generation;
    final long rolledAt;

    LootSession(ItemStack[] contents, int generation, long rolledAt) {
        this.contents = contents;
        this.generation = generation;
        this.rolledAt = rolledAt;
    }

    boolean isEmpty() {
        for (ItemStack s : contents) {
            if (s != null) {
                return false;
            }
        }
        return true;
    }
}
