package com.deadzone.modules.medicine.item;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Kit de Primeiros Socorros (T2): cura ferimentos graves ou revive um jogador derrubado. */
public class KitPrimeirosSocorros extends DefinedItem {

    public KitPrimeirosSocorros(DeadzonePlugin plugin, ItemDefinition def) {
        super(plugin, def);
    }

    @Override
    @SuppressWarnings("deprecation") // getMaxHealth é estável entre versões; evita o churn do enum Attribute
    public boolean onUse(Player player, ItemStack stack) {
        // Se mirar num jogador derrubado, revive em vez de curar a si mesmo.
        Player downedTarget = plugin.getClassManager().downed().getTargetDowned(player);
        if (downedTarget != null) {
            if (plugin.getClassManager().downed().tryRevive(player, downedTarget)) {
                consumeOne(stack);
            }
            return true;
        }
        if (!checkAndApplyCooldown(player)) {
            return true;
        }
        double heal = def.useInt("heal", 12);
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + heal));
        consumeOne(stack);
        player.playSound(player, Sound.BLOCK_WOOL_PLACE, 1.0f, 0.9f);
        player.sendActionBar(Component.text("Ferimentos tratados.", NamedTextColor.GREEN));
        return true;
    }
}
