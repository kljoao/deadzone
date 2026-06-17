package com.deadzone.modules.clan.gui;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Helpers de construção de itens para os menus de clan. */
final class ClanGui {

    private ClanGui() {
    }

    static ItemStack item(DeadzonePlugin plugin, Material mat, String nameMini, String... loreMini) {
        ItemStack it = new ItemStack(mat);
        it.editMeta(m -> {
            m.displayName(plugin.getMessages().parse(nameMini).decoration(TextDecoration.ITALIC, false));
            if (loreMini.length > 0) {
                List<Component> lore = new ArrayList<>();
                for (String l : loreMini) {
                    lore.add(plugin.getMessages().parse(l).decoration(TextDecoration.ITALIC, false));
                }
                m.lore(lore);
            }
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return it;
    }

    static ItemStack filler() {
        ItemStack f = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        f.editMeta(m -> m.displayName(Component.empty()));
        return f;
    }
}
