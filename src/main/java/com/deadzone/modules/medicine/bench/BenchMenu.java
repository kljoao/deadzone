package com.deadzone.modules.medicine.bench;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import com.deadzone.modules.medicine.MedicineManager;
import com.deadzone.modules.medicine.item.ItemDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** GUI de crafting da Bancada Médica: mostra só o que o jogador pode fabricar e craftar. */
public class BenchMenu extends Menu {

    private final DeadzonePlugin plugin;

    public BenchMenu(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Component title() {
        return Component.text("Bancada Médica", NamedTextColor.DARK_RED);
    }

    @Override
    public int size() {
        return 54;
    }

    @Override
    protected void build(Player viewer) {
        MedicineManager medicine = plugin.getMedicineManager();
        List<ItemDefinition> craftable = medicine.craftableFor(viewer);

        int slot = 0;
        for (ItemDefinition def : craftable) {
            if (slot >= 45) {
                break; // última linha fica como moldura
            }
            setItem(slot, icon(viewer, def), event -> craft((Player) event.getWhoClicked(), def));
            slot++;
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.empty()));
        fillEmpty(filler);
    }

    private ItemStack icon(Player viewer, ItemDefinition def) {
        MedicineManager medicine = plugin.getMedicineManager();
        ItemStack icon = plugin.getItemRegistry().get(def.id()).build();
        icon.editMeta(meta -> {
            List<Component> lore = new ArrayList<>();
            if (meta.hasLore()) {
                lore.addAll(meta.lore());
            }
            lore.add(Component.empty());
            lore.add(Component.text("Ingredientes:", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            for (Map.Entry<String, Integer> entry : def.recipe().entrySet()) {
                int have = medicine.countIngredient(viewer, entry.getKey());
                int need = entry.getValue();
                NamedTextColor color = have >= need ? NamedTextColor.GREEN : NamedTextColor.RED;
                lore.add(Component.text("• " + label(entry.getKey()) + ": " + have + "/" + need, color)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Clique para fabricar", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
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

    private String label(String key) {
        com.deadzone.core.item.CustomItem custom = plugin.getItemRegistry().get(key);
        if (custom != null) {
            return key; // id do item custom
        }
        return key.toLowerCase().replace('_', ' ');
    }
}
