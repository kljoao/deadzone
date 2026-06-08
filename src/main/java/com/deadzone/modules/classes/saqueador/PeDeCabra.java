package com.deadzone.modules.classes.saqueador;

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

/** Pé de Cabra (Saqueador): abre contêineres trancados. */
public class PeDeCabra extends CustomItem {

    public static final String ID = "pe_de_cabra";

    private final DeadzonePlugin plugin;

    public PeDeCabra(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ItemStack build() {
        ItemStack stack = new ItemStack(Material.STICK);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("Pé de Cabra", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Saqueador: abre contêineres trancados.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
            meta.setCustomModelData(2002);
            meta.getPersistentDataContainer().set(ItemKeys.ITEM_ID, PersistentDataType.STRING, id());
        });
        return stack;
    }
}
