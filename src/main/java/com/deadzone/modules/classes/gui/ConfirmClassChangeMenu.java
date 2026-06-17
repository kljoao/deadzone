package com.deadzone.modules.classes.gui;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import com.deadzone.core.profile.PlayerClass;
import com.deadzone.core.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Confirmação de troca de classe — avisa que XP e habilidades serão zerados. */
public class ConfirmClassChangeMenu extends Menu {

    private final DeadzonePlugin plugin;
    private final PlayerClass target;

    public ConfirmClassChangeMenu(DeadzonePlugin plugin, PlayerClass target) {
        this.plugin = plugin;
        this.target = target;
    }

    @Override
    public Component title() {
        return Component.text("Trocar de classe?", NamedTextColor.DARK_RED);
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    protected void build(Player viewer) {
        setItem(11, item(Material.LIME_CONCRETE,
                        Component.text("Confirmar troca", NamedTextColor.GREEN),
                        List.of(
                                Component.text("Vira " + target.name() + ".", NamedTextColor.GRAY),
                                Component.empty(),
                                Component.text("⚠ Seu XP e TODAS as", NamedTextColor.RED),
                                Component.text("habilidades serão ZERADOS!", NamedTextColor.RED),
                                Component.text("Não dá para desfazer.", NamedTextColor.DARK_GRAY))),
                e -> {
                    PlayerProfile profile = plugin.getProfileManager().get(viewer.getUniqueId());
                    if (profile != null) {
                        plugin.getClassManager().changeClass(viewer, profile, target);
                    }
                    viewer.closeInventory();
                });

        setItem(15, item(Material.RED_CONCRETE,
                        Component.text("Cancelar", NamedTextColor.RED),
                        List.of(Component.text("Mantém sua classe atual.", NamedTextColor.GRAY))),
                e -> new ClassMenu(plugin).open(viewer));

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.empty()));
        fillEmpty(filler);
    }

    private ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        });
        return stack;
    }
}
