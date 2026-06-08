package com.deadzone.modules.medicine.item;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Antídoto (T2): suprime temporariamente os sintomas da infecção, mas não cura o medidor. */
public class Antidoto extends DefinedItem {

    public Antidoto(DeadzonePlugin plugin, ItemDefinition def) {
        super(plugin, def);
    }

    @Override
    public boolean onUse(Player player, ItemStack stack) {
        if (!checkAndApplyCooldown(player)) {
            return true;
        }
        int seconds = def.useInt("suppress-seconds", 45);
        plugin.getInfectionManager().suppressSymptoms(player.getUniqueId(), seconds);
        consumeOne(stack);
        player.playSound(player, Sound.ITEM_HONEY_BOTTLE_DRINK, 1.0f, 1.0f);
        player.sendActionBar(Component.text("Sintomas suprimidos por " + seconds + "s.", NamedTextColor.GREEN));
        return true;
    }
}
