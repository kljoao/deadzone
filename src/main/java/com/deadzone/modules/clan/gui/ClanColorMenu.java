package com.deadzone.modules.clan.gui;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import com.deadzone.modules.clan.ClanManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Seletor de cor da tag (VIP+). Cada cor permitida vira um botão. */
public class ClanColorMenu extends Menu {

    private final DeadzonePlugin plugin;

    public ClanColorMenu(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    private ClanManager cm() {
        return plugin.getClanManager();
    }

    @Override
    public Component title() {
        return Component.text("Cor da Tag", NamedTextColor.AQUA);
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    protected void build(Player viewer) {
        int slot = 0;
        for (String color : cm().config().allowedColors()) {
            if (slot >= 18) {
                break;
            }
            String low = color.toLowerCase(Locale.ROOT);
            setItem(slot++, ClanGui.item(plugin, wool(color), "<" + low + ">" + color,
                            "<gray>Clique para usar esta cor."),
                    e -> {
                        cm().setColor(viewer, color);
                        new ClanSettingsMenu(plugin).open(viewer);
                    });
        }
        setItem(22, ClanGui.item(plugin, Material.ARROW, "<yellow>Voltar"),
                e -> new ClanSettingsMenu(plugin).open(viewer));
        fillEmpty(ClanGui.filler());
    }

    private Material wool(String color) {
        return switch (color.toUpperCase(Locale.ROOT)) {
            case "WHITE" -> Material.WHITE_WOOL;
            case "GRAY", "DARK_GRAY" -> Material.GRAY_WOOL;
            case "YELLOW" -> Material.YELLOW_WOOL;
            case "GOLD" -> Material.ORANGE_WOOL;
            case "RED", "DARK_RED" -> Material.RED_WOOL;
            case "GREEN" -> Material.LIME_WOOL;
            case "DARK_GREEN" -> Material.GREEN_WOOL;
            case "AQUA", "DARK_AQUA" -> Material.LIGHT_BLUE_WOOL;
            case "BLUE" -> Material.BLUE_WOOL;
            case "LIGHT_PURPLE" -> Material.MAGENTA_WOOL;
            case "DARK_PURPLE" -> Material.PURPLE_WOOL;
            default -> Material.WHITE_WOOL;
        };
    }
}
