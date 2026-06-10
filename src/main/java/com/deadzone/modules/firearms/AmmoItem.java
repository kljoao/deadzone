package com.deadzone.modules.firearms;

import com.deadzone.core.item.CustomItem;
import com.deadzone.core.item.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Item de munição (ex.: 9mm). Identidade no PDC ITEM_ID; empilhável. */
public class AmmoItem extends CustomItem {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final AmmoType type;

    public AmmoItem(AmmoType type) {
        this.type = type;
    }

    @Override
    public String id() {
        return type.id();
    }

    @Override
    public ItemStack build() {
        ItemStack stack = new ItemStack(type.material());
        stack.editMeta(meta -> {
            meta.displayName(MM.deserialize(type.displayName()).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : type.lore()) {
                lore.add(MM.deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            if (type.customModelData() > 0) {
                meta.setCustomModelData(type.customModelData());
            }
            meta.getPersistentDataContainer().set(ItemKeys.ITEM_ID, PersistentDataType.STRING, type.id());
        });
        return stack;
    }
}
