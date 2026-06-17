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

/**
 * Rádio de Frequência (Saqueador): revela baús com itens por perto (Sexto Sentido).
 */
public class RadioFrequencia extends CustomItem {

    public static final String ID = "radio_frequencia";

    private final DeadzonePlugin plugin;

    public RadioFrequencia(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ItemStack build() {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("Rádio de Frequência", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Saqueador: revela baús com loot.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Clique direito para emitir um ping.", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
            meta.setCustomModelData(6002);
            meta.getPersistentDataContainer().set(ItemKeys.ITEM_ID, PersistentDataType.STRING, id());
        });
        return stack;
    }

    @Override
    public boolean onUse(Player player, ItemStack stack) {
        plugin.getClassManager().saqueador().usePing(player);
        return true; // nunca é consumido
    }
}
