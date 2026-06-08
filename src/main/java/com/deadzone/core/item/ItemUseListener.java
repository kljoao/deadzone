package com.deadzone.core.item;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Dispatcher único de clique direito: resolve o item pelo PDC e chama onUse. */
public class ItemUseListener implements Listener {

    private final ItemRegistry registry;

    public ItemUseListener(ItemRegistry registry) {
        this.registry = registry;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Evita disparo duplo (mão principal + secundária).
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        registry.resolve(item).ifPresent(customItem -> {
            if (customItem.onUse(event.getPlayer(), item)) {
                event.setCancelled(true);
            }
        });
    }
}
