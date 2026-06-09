package com.deadzone.modules.classes.saqueador;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.item.CustomItem;
import com.deadzone.core.item.ItemKeys;
import com.deadzone.modules.classes.ClassConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Pé de Cabra (Saqueador): arma de contusão com durabilidade que também abre
 * contêineres trancados. A lógica de abertura fica no {@link SaqueadorListener}.
 */
public class PeDeCabra extends CustomItem {

    public static final String ID = "pe_de_cabra";

    private final DeadzonePlugin plugin;

    public PeDeCabra(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ItemStack build() {
        ClassConfig cfg = plugin.getClassManager().config();
        ItemStack stack = new ItemStack(Material.STICK);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("Pé de Cabra", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Saqueador: abre contêineres trancados.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Dano: " + (int) cfg.crowbarDamage(), NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
            meta.setCustomModelData(2002);
            meta.getPersistentDataContainer().set(ItemKeys.ITEM_ID, PersistentDataType.STRING, id());

            meta.setMaxStackSize(1);
            if (meta instanceof Damageable damageable) {
                damageable.setMaxDamage(cfg.crowbarDurability());
            }

            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(
                    new NamespacedKey(plugin, "crowbar_damage"),
                    cfg.crowbarDamage() - 1.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(
                    new NamespacedKey(plugin, "crowbar_speed"),
                    cfg.crowbarAttackSpeed() - 4.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND));
        });
        return stack;
    }
}
