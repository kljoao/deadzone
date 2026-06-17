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

/** Kit de Primeiros Socorros (T2): efeitos de maçã dourada, ou revive um jogador derrubado. */
public class KitPrimeirosSocorros extends DefinedItem {

    public KitPrimeirosSocorros(DeadzonePlugin plugin, ItemDefinition def) {
        super(plugin, def);
    }

    @Override
    public boolean onUse(Player player, ItemStack stack) {
        // Se mirar num jogador derrubado, reanima (não exige Médico).
        Player downedTarget = plugin.getClassManager().downed().getTargetDowned(player);
        if (downedTarget != null) {
            // Consome só quando o revive CONCLUIR (não ao iniciar).
            plugin.getClassManager().downed().startRevive(player, downedTarget, false, () -> consumeOne(stack));
            return true;
        }
        if (!checkAndApplyCooldown(player)) {
            return true;
        }
        // Mesmos efeitos de uma maçã dourada: Absorção I (2 min) + Regeneração II (5s).
        addEffect(player, "absorption", 0, 2400);
        addEffect(player, "regeneration", 1, 100);
        consumeOne(stack);
        player.playSound(player, Sound.ITEM_TOTEM_USE, 0.7f, 1.4f);
        player.sendActionBar(Component.text("Ferimentos tratados.", NamedTextColor.GREEN));
        return true;
    }

    private void addEffect(Player player, String id, int amplifier, int ticks) {
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(id));
        if (type != null) {
            player.addPotionEffect(new PotionEffect(type, ticks, amplifier, true, true, true));
        }
    }
}
