package com.deadzone.modules.claim;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** Helpers para criar ícones dos menus de base. */
final class ClaimIcons {

    private ClaimIcons() {
    }

    static ItemStack item(Material material, Component name, Component... lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            if (lore.length > 0) {
                List<Component> lines = new ArrayList<>();
                for (Component c : lore) {
                    lines.add(c.decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lines);
            }
        });
        return stack;
    }

    static ItemStack head(OfflinePlayer owner, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        stack.editMeta(meta -> {
            if (meta instanceof SkullMeta skull) {
                skull.setOwningPlayer(owner);
            }
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            List<Component> lines = new ArrayList<>();
            for (Component c : lore) {
                lines.add(c.decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lines);
        });
        return stack;
    }

    static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
    }
}
