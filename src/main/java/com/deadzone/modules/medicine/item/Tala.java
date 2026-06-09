package com.deadzone.modules.medicine.item;

import com.deadzone.DeadzonePlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Tala (T1): canalização imóvel que acelera a recuperação da perna quebrada.
 */
public class Tala extends DefinedItem {

    public Tala(DeadzonePlugin plugin, ItemDefinition def) {
        super(plugin, def);
    }

    @Override
    public boolean onUse(Player player, ItemStack stack) {
        plugin.getMedicineManager().brokenLeg().useSplint(player, stack);
        return true;
    }
}
