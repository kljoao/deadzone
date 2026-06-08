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

import java.util.ArrayList;
import java.util.List;

/**
 * Menu de seleção de classe (/classe). Reaberto automaticamente no respawn.
 */
public class ClassMenu extends Menu {

    private final DeadzonePlugin plugin;

    public ClassMenu(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Component title() {
        return Component.text("Escolha sua Classe", NamedTextColor.DARK_RED);
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    protected void build(Player viewer) {
        PlayerProfile profile = plugin.getProfileManager().get(viewer.getUniqueId());
        PlayerClass current = profile != null ? profile.getPlayerClass() : PlayerClass.NONE;

        setItem(11, icon(PlayerClass.MEDICO, Material.GOLDEN_APPLE, "Médico",
                List.of("Suporte: cura, diagnóstico,", "reanimação e farmácia avançada."), current),
                e -> choose((Player) e.getWhoClicked(), PlayerClass.MEDICO));
        setItem(13, icon(PlayerClass.BRUTO, Material.IRON_AXE, "Bruto",
                List.of("Linha de frente: resistência viral", "e atordoamento de zumbis."), current),
                e -> choose((Player) e.getWhoClicked(), PlayerClass.BRUTO));
        setItem(15, icon(PlayerClass.SAQUEADOR, Material.CHEST, "Saqueador",
                List.of("Explorador: detecção de loot", "e arrombamento de contêineres."), current),
                e -> choose((Player) e.getWhoClicked(), PlayerClass.SAQUEADOR));

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.empty()));
        fillEmpty(filler);
    }

    private ItemStack icon(PlayerClass clazz, Material material, String name, List<String> desc, PlayerClass current) {
        ItemStack item = new ItemStack(material);
        boolean isCurrent = current == clazz;
        item.editMeta(meta -> {
            meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : desc) {
                lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            if (isCurrent) {
                lore.add(Component.text("✔ Sua classe atual", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                meta.setEnchantmentGlintOverride(true);
            } else {
                lore.add(Component.text("Clique para escolher", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        });
        return item;
    }

    private void choose(Player player, PlayerClass clazz) {
        PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
        if (profile == null) {
            return;
        }
        if (!plugin.getClassManager().canSelect(profile)) {
            player.sendMessage(Component.text("Você já escolheu sua classe nesta vida.", NamedTextColor.RED));
            player.closeInventory();
            return;
        }
        plugin.getClassManager().selectClass(player, profile, clazz);
        player.closeInventory();
    }
}
