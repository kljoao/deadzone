package com.deadzone.modules.medicine.bench;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import com.deadzone.modules.medicine.MedicineManager;
import com.deadzone.modules.medicine.item.ItemDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Lista de itens de uma seção da bancada (Médica/Armeiro). Itens sem a skill ficam cinza com aviso. */
public class BenchSectionMenu extends Menu {

    private final DeadzonePlugin plugin;
    private final String section; // "medica" | "armeiro"

    public BenchSectionMenu(DeadzonePlugin plugin, String section) {
        this.plugin = plugin;
        this.section = section;
    }

    @Override
    public Component title() {
        return Component.text("✚ Bancada — " + sectionName(), NamedTextColor.DARK_RED);
    }

    private String sectionName() {
        return switch (section) {
            case "armeiro_attachments" -> "Anexos";
            case "armeiro_armas" -> "Armas";
            case "armeiro" -> "Armeiro";
            default -> "Médica";
        };
    }

    @Override
    public int size() {
        return 54;
    }

    @Override
    protected void build(Player viewer) {
        MedicineManager medicine = plugin.getMedicineManager();

        ItemStack edge = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) {
            setItem(i, edge);
        }
        for (int i = 45; i < 54; i++) {
            setItem(i, edge);
        }

        int slot = 9;
        List<ItemDefinition> items = medicine.byCategory(section);
        for (ItemDefinition def : items) {
            if (slot >= 45) {
                break;
            }
            boolean unlocked = medicine.hasSkillFor(viewer, def);
            setItem(slot, icon(viewer, def, unlocked), event -> {
                if (!unlocked) {
                    viewer.playSound(viewer, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
                    viewer.sendActionBar(Component.text("Você ainda não sabe fabricar isso.", NamedTextColor.RED));
                    return;
                }
                craft(viewer, def);
            });
            slot++;
        }
        if (items.isEmpty()) {
            setItem(22, info("Nada por aqui ainda."));
        }

        setItem(49, backButton(), e -> backTo(viewer));
        fillEmpty(pane(Material.GRAY_STAINED_GLASS_PANE));
    }

    /** Sub-seções do Armeiro voltam pro sub-hub; o resto volta pro menu principal. */
    private void backTo(Player viewer) {
        if (section.startsWith("armeiro_")) {
            new BenchArmeiroMenu(plugin).open(viewer);
        } else {
            new BenchMenu(plugin).open(viewer);
        }
    }

    private ItemStack backButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        item.editMeta(meta -> meta.displayName(Component.text("← Voltar", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)));
        return item;
    }

    private ItemStack icon(Player viewer, ItemDefinition def, boolean unlocked) {
        MedicineManager medicine = plugin.getMedicineManager();
        com.deadzone.core.item.CustomItem product = plugin.getItemRegistry().get(def.id());
        if (product == null) {
            return info("⚠ " + def.id() + " (não registrado)");
        }
        ItemStack icon = product.build();
        icon.editMeta(meta -> {
            if (!unlocked) {
                String plain = meta.hasDisplayName()
                        ? PlainTextComponentSerializer.plainText().serialize(meta.displayName()) : def.id();
                meta.displayName(Component.text("🔒 " + plain, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            List<Component> lore = new ArrayList<>();
            if (meta.hasLore()) {
                lore.addAll(meta.lore());
            }
            lore.add(Component.empty());
            if (!unlocked) {
                lore.add(Component.text("🔒 Requer a habilidade:", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("   " + skillName(def.requiredSkill()), NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
            }
            lore.add(Component.text("Ingredientes:", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            for (Map.Entry<String, Integer> entry : def.recipe().entrySet()) {
                int have = medicine.countIngredient(viewer, entry.getKey());
                int need = entry.getValue();
                NamedTextColor color = have >= need ? NamedTextColor.GREEN : NamedTextColor.RED;
                lore.add(Component.text("• " + label(entry.getKey()) + ": " + have + "/" + need, color)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(unlocked
                    ? Component.text("Clique para fabricar", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    : Component.text("Bloqueado", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return icon;
    }

    private void craft(Player player, ItemDefinition def) {
        MedicineManager.CraftResult result = plugin.getMedicineManager().craft(player, def);
        switch (result) {
            case SUCCESS -> {
                player.playSound(player, Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.2f);
                refresh(player);
            }
            case MISSING_INGREDIENTS -> {
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
                player.sendActionBar(Component.text("Faltam ingredientes.", NamedTextColor.RED));
            }
            case NO_SKILL -> player.sendActionBar(
                    Component.text("Você não sabe fabricar isso.", NamedTextColor.RED));
        }
    }

    private String skillName(String skillId) {
        if (skillId == null) {
            return "?";
        }
        String s = skillId.contains("_") ? skillId.substring(skillId.indexOf('_') + 1) : skillId;
        return s.replace('_', ' ');
    }

    private String label(String key) {
        com.deadzone.core.item.CustomItem custom = plugin.getItemRegistry().get(key);
        if (custom != null) {
            return key;
        }
        return key.toLowerCase().replace('_', ' ');
    }

    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(Component.empty()));
        return item;
    }

    private ItemStack info(String text) {
        ItemStack item = new ItemStack(Material.BARRIER);
        item.editMeta(meta -> meta.displayName(Component.text(text, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        return item;
    }
}
