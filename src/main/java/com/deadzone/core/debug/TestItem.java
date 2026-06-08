package com.deadzone.core.debug;

import com.deadzone.core.config.Messages;
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

/** Item de teste para validar o framework de itens customizados (PDC + onUse). */
public class TestItem extends CustomItem {

    private final Messages messages;

    public TestItem(Messages messages) {
        this.messages = messages;
    }

    @Override
    public String id() {
        return "test_token";
    }

    @Override
    public ItemStack build() {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("Token de Teste", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Item de teste do Deadzone.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Clique direito para testar.", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
            meta.setCustomModelData(9999);
            meta.getPersistentDataContainer().set(ItemKeys.ITEM_ID, PersistentDataType.STRING, id());
        });
        return stack;
    }

    @Override
    public boolean onUse(Player player, ItemStack stack) {
        messages.send(player, "test-item-used");
        return true;
    }
}
