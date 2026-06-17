package com.deadzone.modules.medicine.bench;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Sub-hub do Armeiro: escolha entre Anexos (attachments) e Armas. */
public class BenchArmeiroMenu extends Menu {

    private final DeadzonePlugin plugin;

    public BenchArmeiroMenu(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Component title() {
        return Component.text("✚ Bancada — Armeiro", NamedTextColor.DARK_RED);
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    protected void build(Player viewer) {
        setItem(11, option(Material.SPYGLASS, "Anexos",
                        List.of("Silenciador e mira 8x", "para aplicar nas armas.")),
                e -> new BenchSectionMenu(plugin, "armeiro_attachments").open(viewer));

        setItem(15, option(Material.CROSSBOW, "Armas",
                        List.of("Fabrique e modifique", "armas de fogo.")),
                e -> new BenchSectionMenu(plugin, "armeiro_armas").open(viewer));

        setItem(22, backButton(), e -> new BenchMenu(plugin).open(viewer));

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.empty()));
        fillEmpty(filler);
    }

    private ItemStack backButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        item.editMeta(meta -> meta.displayName(Component.text("← Voltar", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)));
        return item;
    }

    private ItemStack option(Material material, String name, List<String> desc) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : desc) {
                lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Clique para abrir", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        });
        return item;
    }
}
