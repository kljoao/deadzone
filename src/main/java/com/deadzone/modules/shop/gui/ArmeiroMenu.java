package com.deadzone.modules.shop.gui;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Seletor do armeiro: Armas ou Modificações (layout emoldurado). */
public class ArmeiroMenu extends Menu {

    private final DeadzonePlugin plugin;

    public ArmeiroMenu(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Component title() {
        return Component.text("Armeiro", NamedTextColor.DARK_GRAY);
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    protected void build(Player viewer) {
        setItem(4, ShopGui.named(plugin, Material.SMITHING_TABLE, "<gray><bold>Armeiro",
                "<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬",
                "<gray>Escolha o que deseja ver."));

        setItem(11, ShopGui.named(plugin, Material.IRON_INGOT, "<red><bold>⚔ Armas",
                        "<gray>Pistolas, fuzis e munição.",
                        "",
                        "<green>▶ Clique para abrir"),
                e -> new ShopMenu(plugin, "armeiro_armas").open(viewer));
        setItem(15, ShopGui.named(plugin, Material.AMETHYST_SHARD, "<light_purple><bold>✦ Modificações",
                        "<gray>Silenciador, mira e anexos.",
                        "",
                        "<green>▶ Clique para abrir"),
                e -> new ShopMenu(plugin, "armeiro_mods").open(viewer));

        setItem(22, ShopGui.named(plugin, Material.SUNFLOWER, "<gold><bold>Seu saldo",
                "<yellow>" + plugin.getEconomyManager().format(plugin.getEconomyManager().balanceOf(viewer))));

        fillEmpty(ShopGui.pane(Material.GRAY_STAINED_GLASS_PANE));
    }
}
