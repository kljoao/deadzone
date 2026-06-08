package com.deadzone.modules.medicine.item;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Bandagem (T1): estanca o sangramento, mas não cura vida nem dá regeneração. */
public class Bandagem extends DefinedItem {

    public Bandagem(DeadzonePlugin plugin, ItemDefinition def) {
        super(plugin, def);
    }

    @Override
    public boolean onUse(Player player, ItemStack stack) {
        PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
        if (profile == null) {
            return false;
        }
        if (profile.getBleedState() == null) {
            player.sendActionBar(Component.text("Você não está sangrando.", NamedTextColor.GRAY));
            return true; // cancela a interação, mas não gasta o item
        }
        if (plugin.getMedicineManager().bandage().isChanneling(player.getUniqueId())) {
            return true; // já está aplicando
        }
        long cooldown = plugin.getMedicineManager().cooldownRemaining(player, id());
        if (cooldown > 0) {
            player.sendActionBar(Component.text("Aguarde " + cooldown + "s para usar de novo.", NamedTextColor.GRAY));
            return true;
        }
        // Inicia a canalização de 5s (imóvel). O item é consumido ao concluir.
        plugin.getMedicineManager().bandage().start(player, this);
        return true;
    }
}
