package com.deadzone.modules.claim;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.item.CustomItem;
import com.deadzone.core.item.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Livro de Base: segure para ver a área; clique direito para travar; /confirmar base para criar. */
public class LivroDeBase extends CustomItem {

    private final DeadzonePlugin plugin;
    private final ClaimManager manager;

    public LivroDeBase(DeadzonePlugin plugin, ClaimManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String id() {
        return ClaimManager.BOOK_ID;
    }

    @Override
    public boolean onUse(Player player, ItemStack stack) {
        manager.lockPending(player);
        return true;
    }

    @Override
    public ItemStack build() {
        ItemStack stack = new ItemStack(Material.BOOK);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("Livro de Base", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Reivindica uma área 20x20 como sua base.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Segure: mostra a área (verde = livre).", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Clique direito: trava a posição.", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("/confirmar base: cria a base.", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
            meta.setMaxStackSize(1);
            meta.getPersistentDataContainer().set(ItemKeys.ITEM_ID, PersistentDataType.STRING, id());
        });
        return stack;
    }
}
