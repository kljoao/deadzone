package com.deadzone.core.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Registro central de itens customizados; resolve um ItemStack pelo ITEM_ID do PDC. */
public class ItemRegistry {

    private final Map<String, CustomItem> byId = new LinkedHashMap<>();

    public void register(CustomItem item) {
        byId.put(item.id(), item);
    }

    public CustomItem get(String id) {
        return byId.get(id);
    }

    public Collection<CustomItem> all() {
        return byId.values();
    }

    /** Resolve o CustomItem de um stack, ou empty se não for um item nosso. */
    public Optional<CustomItem> resolve(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        String id = stack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.ITEM_ID, PersistentDataType.STRING);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id));
    }
}
