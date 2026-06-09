package com.deadzone.modules.classes.saqueador;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.item.CustomItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * Abertura de contêineres trancados (só Saqueador com Pé de Cabra) e desgaste do Pé de Cabra.
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
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        CustomItem held = plugin.getItemRegistry().resolve(hand).orElse(null);
        boolean usingCrowbar = held != null && held.id().equals(PeDeCabra.ID);

        if (usingCrowbar && manager.canPick(player)) {
            player.playSound(player, Sound.BLOCK_IRON_DOOR_OPEN, 1f, 1.2f);
            damageCrowbar(player, hand, plugin.getClassManager().config().crowbarDurabilityPerUnlock());
            return;
        }
        event.setCancelled(true);
        player.sendActionBar(Component.text("Trancado. Precisa de um Pé de Cabra (Saqueador).", NamedTextColor.RED));
        player.playSound(player, Sound.BLOCK_CHEST_LOCKED, 1f, 1f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean isCrowbar = plugin.getItemRegistry().resolve(hand)
                .map(item -> item.id().equals(PeDeCabra.ID)).orElse(false);
        if (isCrowbar) {
            damageCrowbar(player, hand, plugin.getClassManager().config().crowbarDurabilityPerHit());
        }
    }

    private void damageCrowbar(Player player, ItemStack stack, int amount) {
        if (amount <= 0 || !(stack.getItemMeta() instanceof Damageable meta) || !meta.hasMaxDamage()) {
            return;
        }
        int newDamage = meta.getDamage() + amount;
        if (newDamage >= meta.getMaxDamage()) {
            stack.setAmount(stack.getAmount() - 1);
            player.playSound(player, Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        } else {
            meta.setDamage(newDamage);
            stack.setItemMeta(meta);
        }
    }
}
