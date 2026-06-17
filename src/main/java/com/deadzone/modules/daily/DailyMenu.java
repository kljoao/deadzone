package com.deadzone.modules.daily;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.modules.daily.DailyRewardsConfig.DayReward;
import com.deadzone.modules.daily.DailyRewardsConfig.ItemEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Menu da recompensa diária (/diario): mostra o ciclo de 7 dias e o botão de resgate. */
public class DailyMenu extends Menu {

    private static final int[] DAY_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int CLAIM_SLOT = 31;

    private final DeadzonePlugin plugin;
    private final DailyRewardManager manager;

    public DailyMenu(DeadzonePlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getDailyRewardManager();
    }

    @Override
    public Component title() {
        return Component.text("Recompensa Diária", NamedTextColor.GOLD);
    }

    @Override
    public int size() {
        return 45;
    }

    @Override
    protected void build(Player viewer) {
        PlayerProfile profile = plugin.getProfileManager().get(viewer.getUniqueId());
        if (profile == null) {
            setItem(22, named(new ItemStack(Material.BARRIER), "<red>Perfil carregando...", List.of()));
            fillBorder();
            return;
        }

        DailyRewardsConfig cfg = manager.config();
        int cycle = Math.min(DAY_SLOTS.length, cfg.cycleDays());
        boolean claimed = manager.claimedToday(profile);
        long streak = profile.getDailyStreak();

        // Dia "atual" do ciclo: o já resgatado hoje, ou o que ficaria disponível ao resgatar.
        int currentDay = claimed
                ? cfg.rewardDayFor(streak)
                : cfg.rewardDayFor(manager.streakIfClaimNow(profile));

        for (int i = 0; i < cycle; i++) {
            int day = i + 1;
            DayReward reward = cfg.rewardForDay(day);
            State state = stateFor(day, currentDay, claimed);
            setItem(DAY_SLOTS[i], dayIcon(day, reward, state));
        }

        setItem(CLAIM_SLOT, claimButton(profile, claimed, currentDay),
                claimed ? null : e -> doClaim(viewer));

        fillBorder();
    }

    private enum State { DONE, TODAY, LOCKED }

    private State stateFor(int day, int currentDay, boolean claimedToday) {
        if (claimedToday) {
            return day <= currentDay ? State.DONE : State.LOCKED;
        }
        if (day < currentDay) {
            return State.DONE;
        }
        return day == currentDay ? State.TODAY : State.LOCKED;
    }

    private ItemStack dayIcon(int day, DayReward reward, State state) {
        Material mat = switch (state) {
            case DONE -> Material.LIME_STAINED_GLASS_PANE;
            case TODAY -> Material.CHEST;
            case LOCKED -> Material.GRAY_STAINED_GLASS_PANE;
        };
        ItemStack icon = new ItemStack(mat);
        String dayColor = state == State.TODAY ? "<yellow>" : state == State.DONE ? "<green>" : "<gray>";
        String suffix = switch (state) {
            case DONE -> " <green>✔";
            case TODAY -> " <gold>(hoje!)";
            case LOCKED -> "";
        };
        List<String> lore = new ArrayList<>();
        if (reward.scraps() > 0) {
            lore.add("<gold>+ " + plugin.getEconomyManager().format(reward.scraps()));
        }
        for (ItemEntry e : reward.items()) {
            ItemStack s = manager.config().build(e);
            String name = (s != null && s.hasItemMeta() && s.getItemMeta().hasDisplayName())
                    ? plainName(s) : (e.material() != null ? e.material().name() : e.itemId());
            lore.add("<aqua>+ " + e.amount() + "x <white>" + name);
        }
        if (state == State.TODAY) {
            lore.add("");
            lore.add("<gray>Disponível para resgate!");
        }
        ItemStack out = named(icon, dayColor + "Dia " + day + suffix, lore);
        if (state == State.TODAY) {
            glow(out);
        }
        return out;
    }

    private ItemStack claimButton(PlayerProfile profile, boolean claimed, int currentDay) {
        if (!claimed) {
            long streakIf = manager.streakIfClaimNow(profile);
            List<String> lore = List.of(
                    "<gray>Recompensa do <yellow>Dia " + currentDay + "<gray>.",
                    "<gray>Novo streak: <yellow>" + streakIf + " dia" + (streakIf == 1 ? "" : "s") + "<gray>.",
                    "",
                    "<green>▶ Clique para resgatar!");
            return glow(named(new ItemStack(Material.NETHER_STAR), "<green><bold>RESGATAR RECOMPENSA", lore));
        }
        long ms = manager.msUntilNextClaim(profile);
        List<String> lore = List.of(
                "<gray>Você já resgatou hoje.",
                "<gray>Streak atual: <yellow>" + profile.getDailyStreak() + " dia"
                        + (profile.getDailyStreak() == 1 ? "" : "s") + "<gray>.",
                "",
                "<gray>Próxima recompensa em <white>" + formatDuration(ms) + "<gray>.");
        return named(new ItemStack(Material.CLOCK), "<gray>Volte amanhã!", lore);
    }

    private void doClaim(Player viewer) {
        PlayerProfile profile = plugin.getProfileManager().get(viewer.getUniqueId());
        if (profile == null) {
            return;
        }
        boolean ok = manager.claim(viewer, profile);
        if (!ok) {
            viewer.playSound(viewer, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
        }
        refresh(viewer);
    }

    // ----- helpers de UI -----

    private ItemStack named(ItemStack stack, String miniName, List<String> miniLore) {
        stack.editMeta(meta -> {
            meta.displayName(plugin.getMessages().parse(miniName).decoration(TextDecoration.ITALIC, false));
            if (!miniLore.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : miniLore) {
                    lore.add(plugin.getMessages().parse(line).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lore);
            }
        });
        return stack;
    }

    private ItemStack glow(ItemStack stack) {
        stack.editMeta(meta -> {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
        return stack;
    }

    private String plainName(ItemStack stack) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(stack.getItemMeta().displayName());
    }

    private void fillBorder() {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.empty()));
        fillEmpty(filler);
    }

    private String formatDuration(long ms) {
        long totalMin = ms / 60000L;
        long h = totalMin / 60;
        long m = totalMin % 60;
        if (h > 0) {
            return h + "h " + m + "min";
        }
        return Math.max(1, m) + "min";
    }
}
