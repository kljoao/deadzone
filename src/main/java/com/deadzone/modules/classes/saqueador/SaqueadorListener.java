package com.deadzone.modules.classes.saqueador;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.item.CustomItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Bloqueia a abertura de contêineres trancados, exceto para Saqueadores com Pé de Cabra.
 */
public class SaqueadorListener implements Listener {

    private final DeadzonePlugin plugin;
    private final SaqueadorManager manager;

    public SaqueadorListener(DeadzonePlugin plugin, SaqueadorManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !manager.isLocked(block)) {
            return;
        }
        var player = event.getPlayer();
        CustomItem held = plugin.getItemRegistry().resolve(player.getInventory().getItemInMainHand()).orElse(null);
        boolean usingCrowbar = held != null && held.id().equals(PeDeCabra.ID);

        if (usingCrowbar && manager.canPick(player)) {
            // Saqueador com Pé de Cabra: deixa abrir.
            player.playSound(player, Sound.BLOCK_IRON_DOOR_OPEN, 1f, 1.2f);
            return;
        }
        event.setCancelled(true);
        player.sendActionBar(Component.text("Trancado. Precisa de um Pé de Cabra (Saqueador).", NamedTextColor.RED));
        player.playSound(player, Sound.BLOCK_CHEST_LOCKED, 1f, 1f);
    }
}
