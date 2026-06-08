package com.deadzone.modules.medicine.item;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Seringa de Adrenalina (T2, exclusiva do Médico): restaura fôlego e dá impulso imediato. */
public class SeringaAdrenalina extends DefinedItem {

    public SeringaAdrenalina(DeadzonePlugin plugin, ItemDefinition def) {
        super(plugin, def);
    }

    @Override
    public boolean onUse(Player player, ItemStack stack) {
        if (!checkAndApplyCooldown(player)) {
            return true;
        }
        int seconds = def.useInt("speed-seconds", 12);
        int amplifier = def.useInt("speed-amplifier", 1);
        PotionEffectType speed = Registry.EFFECT.get(NamespacedKey.minecraft("speed"));
        if (speed != null) {
            player.addPotionEffect(new PotionEffect(speed, seconds * 20, amplifier, true, true, true));
        }
        player.setFoodLevel(20);
        player.setSaturation(Math.min(20f, player.getSaturation() + 8f));
        var profile = plugin.getProfileManager().get(player.getUniqueId());
        if (profile != null) {
            plugin.getSanityManager().add(profile, def.useInt("sanity", 0));
        }
        consumeOne(stack);
        player.playSound(player, Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.4f);
        player.sendActionBar(Component.text("Adrenalina na veia!", NamedTextColor.GOLD));
        return true;
    }
}
