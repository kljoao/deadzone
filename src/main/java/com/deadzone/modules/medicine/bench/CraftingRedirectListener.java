package com.deadzone.modules.medicine.bench;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Desabilita o crafting tradicional: a mesa de trabalho abre a Bancada e a grade vanilla não produz. */
public class CraftingRedirectListener implements Listener {

    private final DeadzonePlugin plugin;

    public CraftingRedirectListener(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    /** Clicar na mesa de trabalho abre a Bancada Médica (em vez da grade vanilla). */
    @EventHandler(ignoreCancelled = true)
    public void onUseTable(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CRAFTING_TABLE) {
            return;
        }
        // Sempre redireciona (inclusive agachado) — o crafting vanilla fica 100% desativado.
        event.setCancelled(true);
        new BenchMenu(plugin).open(event.getPlayer());
    }

    /** Bloqueia qualquer fabricação vanilla (mesa OU grade 2x2 do inventário). */
    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            player.sendActionBar(Component.text("Fabricação só pela Bancada (/bancada).", NamedTextColor.RED));
        }
    }
}
