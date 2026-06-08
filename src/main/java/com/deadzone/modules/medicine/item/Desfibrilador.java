package com.deadzone.modules.medicine.item;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Desfibrilador (T3): revive jogadores derrubados, mirando no caído. */
public class Desfibrilador extends DefinedItem {

    public Desfibrilador(DeadzonePlugin plugin, ItemDefinition def) {
        super(plugin, def);
    }

    @Override
    public boolean onUse(Player player, ItemStack stack) {
        Player downedTarget = plugin.getClassManager().downed().getTargetDowned(player);
        if (downedTarget == null) {
            player.sendActionBar(Component.text("Mire em um jogador derrubado para reanimá-lo.", NamedTextColor.GRAY));
            return true;
        }
        if (plugin.getClassManager().downed().tryRevive(player, downedTarget)) {
            consumeOne(stack);
        }
        return true;
    }
}
