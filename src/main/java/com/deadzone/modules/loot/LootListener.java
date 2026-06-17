package com.deadzone.modules.loot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Abre a busca de loot ao clicar num baú marcado e protege o bloco de ser quebrado. */
public class LootListener implements Listener {

    private final LootManager manager;

    public LootListener(LootManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        LootContainer container = manager.getContainer(block);
        if (container == null) {
            return;
        }
        event.setCancelled(true); // não abre o baú vanilla — a busca custom controla tudo
        manager.openSearch(event.getPlayer(), container);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (manager.getContainer(event.getBlock()) != null) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("Baú de loot — use /loot unmark para remover.",
                    NamedTextColor.RED));
        }
    }
}
