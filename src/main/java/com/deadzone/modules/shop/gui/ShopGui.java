package com.deadzone.modules.shop.gui;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Helpers visuais compartilhados pelos menus de loja (borda, ícones, lore). */
final class ShopGui {

    private ShopGui() {
    }

    /** Slots da "área de conteúdo" (miolo 4x7) num inventário de 54, deixando borda nas laterais. */
    static final int[] CONTENT = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    static Component line(DeadzonePlugin plugin, String mini) {
        return plugin.getMessages().parse(mini).decoration(TextDecoration.ITALIC, false);
    }

    static ItemStack pane(Material mat) {
        ItemStack p = new ItemStack(mat);
        p.editMeta(m -> m.displayName(Component.empty()));
        return p;
    }

    static ItemStack named(DeadzonePlugin plugin, Material mat, String nameMini, String... loreMini) {
        ItemStack it = new ItemStack(mat);
        decorate(plugin, it, nameMini, loreMini);
        return it;
    }

    /** Aplica nome + lore (MiniMessage) num stack já existente, preservando a lore anterior. */
    static void decorate(DeadzonePlugin plugin, ItemStack it, String nameMini, String... loreMini) {
        it.editMeta(m -> {
            if (nameMini != null) {
                m.displayName(line(plugin, nameMini));
            }
            List<Component> lore = m.hasLore() ? new ArrayList<>(m.lore()) : new ArrayList<>();
            for (String l : loreMini) {
                lore.add(l.isEmpty() ? Component.empty() : line(plugin, l));
            }
            if (!lore.isEmpty()) {
                m.lore(lore);
            }
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
    }
}
