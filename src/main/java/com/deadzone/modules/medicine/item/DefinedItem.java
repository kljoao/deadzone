package com.deadzone.modules.medicine.item;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.item.CustomItem;
import com.deadzone.core.item.ItemKeys;
import com.deadzone.core.item.Tier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Item cuja aparência vem do items.yml; subclasses implementam só o onUse. */
public abstract class DefinedItem extends CustomItem {

    protected final DeadzonePlugin plugin;
    protected final ItemDefinition def;

    protected DefinedItem(DeadzonePlugin plugin, ItemDefinition def) {
        this.plugin = plugin;
        this.def = def;
    }

    public ItemDefinition definition() {
        return def;
    }

    @Override
    public String id() {
        return def.id();
    }

    @Override
    public Tier tier() {
        return def.tier();
    }

    @Override
    public boolean craftableByEveryone() {
        return def.requiredSkill() == null;
    }

    @Override
    public String requiredSkillId() {
        return def.requiredSkill();
    }

    @Override
    public ItemStack build() {
        ItemStack stack = new ItemStack(def.material());
        stack.editMeta(meta -> {
            meta.displayName(plugin.getMessages().parse(def.name()).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : def.lore()) {
                lore.add(plugin.getMessages().parse(line).decoration(TextDecoration.ITALIC, false));
            }
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            if (def.modelData() > 0) {
                meta.setCustomModelData(def.modelData());
            }
            meta.getPersistentDataContainer().set(ItemKeys.ITEM_ID, PersistentDataType.STRING, id());
        });
        return stack;
    }

    /** Consome 1 unidade do stack na mão. */
    protected void consumeOne(ItemStack stack) {
        stack.setAmount(stack.getAmount() - 1);
    }

    /** Checa cooldown; se em cooldown, avisa o jogador e retorna false (não pode usar). */
    protected boolean checkAndApplyCooldown(Player player) {
        int cd = def.cooldownSeconds();
        if (cd <= 0) {
            return true;
        }
        long remaining = plugin.getMedicineManager().cooldownRemaining(player, id());
        if (remaining > 0) {
            player.sendActionBar(Component.text("Aguarde " + remaining + "s para usar de novo.", NamedTextColor.GRAY));
            return false;
        }
        plugin.getMedicineManager().applyCooldown(player, id(), cd);
        return true;
    }
}
