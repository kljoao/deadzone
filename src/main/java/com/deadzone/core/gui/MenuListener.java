package com.deadzone.core.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Listener único de GUIs: cancela qualquer interação enquanto um menu nosso está aberto
 * e delega o clique ao {@link Menu}.
 */
public class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof MenuHolder menuHolder)) {
            return;
        }
        // Cancela tudo (inclusive cliques no inventário do jogador) p/ impedir mover itens.
        event.setCancelled(true);
        // Só despacha ação se o clique foi no menu (topo).
        if (event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof MenuHolder) {
            menuHolder.getMenu().onClick(event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }
}
