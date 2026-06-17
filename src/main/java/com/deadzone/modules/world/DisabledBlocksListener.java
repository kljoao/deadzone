package com.deadzone.modules.world;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;

import java.util.EnumSet;
import java.util.Set;

/**
 * Desabilita estações vanilla (o servidor usa a Bancada custom) e os funis (sem automação).
 * Bloqueia abrir a GUI dessas estações, colocar funil e qualquer transferência de funil.
 */
public class DisabledBlocksListener implements Listener {

    // Tipos de inventário cuja abertura é bloqueada.
    private static final Set<InventoryType> BLOCKED_GUIS = EnumSet.of(
            InventoryType.FURNACE,
            InventoryType.BLAST_FURNACE,
            InventoryType.SMOKER,
            InventoryType.ANVIL,
            InventoryType.STONECUTTER,
            InventoryType.GRINDSTONE,
            InventoryType.SMITHING,
            InventoryType.CARTOGRAPHY,
            InventoryType.ENCHANTING,
            InventoryType.BREWING,
            InventoryType.HOPPER);

    /** Não abre a GUI das estações desabilitadas. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onOpen(InventoryOpenEvent event) {
        if (!BLOCKED_GUIS.contains(event.getInventory().getType())) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer() instanceof Player player) {
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            player.sendActionBar(Component.text("Estação desabilitada — use a Bancada.", NamedTextColor.RED));
        }
    }

    /** Funis não transferem itens (automação desabilitada). */
    @EventHandler(ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (event.getSource().getType() == InventoryType.HOPPER
                || event.getDestination().getType() == InventoryType.HOPPER) {
            event.setCancelled(true);
        }
    }

    /** Não deixa colocar funil (já que ele não funciona). */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == Material.HOPPER) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            player.sendActionBar(Component.text("Funil desabilitado neste servidor.", NamedTextColor.RED));
        }
    }
}
