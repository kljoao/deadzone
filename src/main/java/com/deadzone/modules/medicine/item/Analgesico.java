package com.deadzone.modules.medicine.item;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Analgésico (T1): remove debuffs leves (náusea, fraqueza, lentidão, fadiga). */
public class Analgesico extends DefinedItem {

    private static final List<String> RELIEVED = List.of("nausea", "weakness", "slowness", "mining_fatigue");

    public Analgesico(DeadzonePlugin plugin, ItemDefinition def) {
        super(plugin, def);
    }

    @Override
    public boolean onUse(Player player, ItemStack stack) {
        if (!checkAndApplyCooldown(player)) {
            return true;
        }
        for (String name : RELIEVED) {
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(name));
            if (type != null) {
                player.removePotionEffect(type);
            }
        }
        var profile = plugin.getProfileManager().get(player.getUniqueId());
        if (profile != null) {
            plugin.getSanityManager().add(profile, def.useInt("sanity", 0));
        }
        consumeOne(stack);
        player.playSound(player, Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.2f);
        player.sendActionBar(Component.text("Você se sente um pouco melhor.", NamedTextColor.GREEN));
        return true;
    }
}
