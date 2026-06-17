package com.deadzone.modules.daily;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Recompensa diária por login (streak). Um resgate por dia (data do servidor): o streak sobe a cada
 * dia consecutivo e zera ao pular um dia; a recompensa cicla (config). Concede scraps + itens.
 */
public class DailyRewardManager {

    private final DeadzonePlugin plugin;
    private final DailyRewardsConfig config;

    public DailyRewardManager(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.config = new DailyRewardsConfig(plugin, configManager);
    }

    public DailyRewardsConfig config() {
        return config;
    }

    public void reload() {
        config.load();
    }

    private static LocalDate dateOf(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** Já resgatou hoje? */
    public boolean claimedToday(PlayerProfile profile) {
        long last = profile.getLastDailyClaim();
        return last > 0 && dateOf(last).isEqual(LocalDate.now());
    }

    /** O streak que o jogador teria SE resgatasse agora (continua, reinicia ou começa). */
    public long streakIfClaimNow(PlayerProfile profile) {
        long last = profile.getLastDailyClaim();
        long streak = profile.getDailyStreak();
        if (last <= 0) {
            return 1;
        }
        LocalDate lastDate = dateOf(last);
        LocalDate today = LocalDate.now();
        if (lastDate.isEqual(today.minusDays(1))) {
            return streak + 1; // dia consecutivo
        }
        if (lastDate.isBefore(today.minusDays(1))) {
            return 1; // pulou um ou mais dias
        }
        return streak; // já é hoje (não deveria ser chamado nesse caso)
    }

    /** Milissegundos até o próximo resgate ficar disponível (0 se já está disponível). */
    public long msUntilNextClaim(PlayerProfile profile) {
        if (!claimedToday(profile)) {
            return 0L;
        }
        ZoneId zone = ZoneId.systemDefault();
        long nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return Math.max(0L, nextMidnight - System.currentTimeMillis());
    }

    /**
     * Resgata a recompensa de hoje. @return true se concedeu; false se já tinha resgatado.
     * Concede scraps + itens (itens que não couberem caem no chão).
     */
    public boolean claim(Player player, PlayerProfile profile) {
        if (claimedToday(profile)) {
            return false;
        }
        long newStreak = streakIfClaimNow(profile);
        DailyRewardsConfig.DayReward reward = config.rewardForStreak(newStreak);

        profile.setDailyStreak(newStreak);
        profile.setLastDailyClaim(System.currentTimeMillis());

        if (reward.scraps() > 0) {
            plugin.getEconomyManager().reward(player, reward.scraps());
        }
        List<ItemStack> granted = new java.util.ArrayList<>();
        for (DailyRewardsConfig.ItemEntry e : reward.items()) {
            ItemStack stack = config.build(e);
            if (stack != null) {
                granted.add(stack);
            }
        }
        if (!granted.isEmpty()) {
            Map<Integer, ItemStack> leftover =
                    player.getInventory().addItem(granted.toArray(new ItemStack[0]));
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }

        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
        player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        player.sendMessage(Component.text("Recompensa diária resgatada! ", NamedTextColor.GREEN)
                .append(Component.text("Streak: " + newStreak + " dia" + (newStreak == 1 ? "" : "s") + ".",
                        NamedTextColor.YELLOW)));
        if (reward.scraps() > 0) {
            player.sendMessage(Component.text("+ " + plugin.getEconomyManager().format(reward.scraps()),
                    NamedTextColor.GOLD));
        }
        for (ItemStack s : granted) {
            player.sendMessage(Component.text("+ " + s.getAmount() + "x ", NamedTextColor.AQUA)
                    .append(itemName(s)));
        }
        return true;
    }

    private Component itemName(ItemStack stack) {
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            return stack.getItemMeta().displayName();
        }
        return Component.translatable(stack.getType().translationKey());
    }
}
