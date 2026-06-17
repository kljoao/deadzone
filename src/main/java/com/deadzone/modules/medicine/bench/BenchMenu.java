package com.deadzone.modules.medicine.bench;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Menu principal da Bancada: escolha a seção (Médica / Armeiro). */
public class BenchMenu extends Menu {

    private final DeadzonePlugin plugin;

    public BenchMenu(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Component title() {
        return Component.text("✚ Bancada", NamedTextColor.DARK_RED);
    }

    @Override
    public int size() {
        return 27;
    }

    // Ícones custom (resource pack): PAPER + custom_model_data.
    private static final int CMD_MEDICINA = 2001;
    private static final int CMD_ARMEIRO = 2002;

    @Override
    protected void build(Player viewer) {
        setItem(11, option(CMD_MEDICINA, "Médica",
                        List.of("Cura, antídotos,", "bandagens e suporte.")),
                e -> new BenchSectionMenu(plugin, "medica").open(viewer));

        setItem(15, option(CMD_ARMEIRO, "Armeiro",
                        List.of("Anexos e armas", "de fogo.")),
                e -> new BenchArmeiroMenu(plugin).open(viewer));

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.empty()));
        fillEmpty(filler);
    }

    private ItemStack option(int modelData, String name, List<String> desc) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> {
            meta.setCustomModelData(modelData);
            meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            java.util.List<Component> lore = new java.util.ArrayList<>();
            for (String line : desc) {
                lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Clique para abrir", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return item;
    }
}
