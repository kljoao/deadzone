package com.deadzone.core.debug;

import com.deadzone.core.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Menu de teste para validar o framework de GUI (holder, clique, ações por slot). */
public class TestMenu extends Menu {

    @Override
    public Component title() {
        return Component.text("Deadzone — Menu de Teste", NamedTextColor.DARK_RED);
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    protected void build(Player viewer) {
        ItemStack confirm = new ItemStack(Material.EMERALD);
        confirm.editMeta(meta -> meta.displayName(
                Component.text("Funciona!", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)));

        setItem(13, confirm, event -> {
            Player player = (Player) event.getWhoClicked();
            player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            player.sendMessage(Component.text("Framework de GUI OK!", NamedTextColor.GREEN));
            player.closeInventory();
        });

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.empty()));
        fillEmpty(filler);
    }
}
