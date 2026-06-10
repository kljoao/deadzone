package com.deadzone.modules.medicine.item;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Antídoto (T2): suprime os sintomas e abaixa um pouco a infecção (não é cura). */
public class Antidoto extends DefinedItem {

    public Antidoto(DeadzonePlugin plugin, ItemDefinition def) {
        super(plugin, def);
    }

    @Override
    public boolean onUse(Player player, ItemStack stack) {
        if (!checkAndApplyCooldown(player)) {
            return true;
        }
        int seconds = def.useInt("suppress-seconds", 90);
        double reduce = def.useInt("reduce-infection", 10);
        plugin.getInfectionManager().suppressSymptoms(player.getUniqueId(), seconds);

        PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
        if (profile != null) {
            profile.setInfectionLevel(Math.max(0.0, profile.getInfectionLevel() - reduce));
        }

        consumeOne(stack);
        player.playSound(player, Sound.ITEM_HONEY_BOTTLE_DRINK, 1.0f, 1.0f);
        player.sendActionBar(Component.text(
                "Sintomas suprimidos (" + seconds + "s) e infecção -" + (int) reduce + "%.",
                NamedTextColor.GREEN));
        return true;
    }
}
