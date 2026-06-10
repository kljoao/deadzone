package com.deadzone.modules.firearms;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.item.CustomItem;
import com.deadzone.core.item.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Arma de fogo. O disparo/recarga ficam no {@link FirearmManager}. */
public class FirearmItem extends CustomItem {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final DeadzonePlugin plugin;
    private final FirearmManager manager;
    private final String id;

    public FirearmItem(DeadzonePlugin plugin, FirearmManager manager, String id) {
        this.plugin = plugin;
        this.manager = manager;
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    public FirearmType type() {
        return manager.type(id);
    }

    @Override
    public boolean onUse(Player player, ItemStack stack) {
        // Botão direito = atira. O botão esquerdo dá a coronhada (ataque melee).
        manager.shoot(player, this, stack);
        return true;
    }

    @Override
    public ItemStack build() {
        FirearmType type = type();
        ItemStack stack = new ItemStack(Material.CARROT_ON_A_STICK);
        stack.editMeta(meta -> {
            meta.displayName(MM.deserialize(type.displayName()).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : type.lore()) {
                lore.add(MM.deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            meta.setCustomModelData(type.customModelData());
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            meta.setMaxStackSize(1);
            meta.getPersistentDataContainer().set(ItemKeys.ITEM_ID, PersistentDataType.STRING, id);
            meta.getPersistentDataContainer().set(ItemKeys.FIREARM_AMMO, PersistentDataType.INTEGER, type.magazineSize());

            // Coronhada: dano de melee (o cooldown de 8s é controlado pelo servidor, não pela arma).
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(
                    new NamespacedKey(plugin, id + "_melee_dmg"), type.meleeDamage() - 1.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        });
        return stack;
    }
}
