package com.deadzone.modules.daily;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.item.CustomItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recompensas diárias (daily-rewards.yml). Define o ciclo (cycle-days) e a recompensa de cada dia
 * (scraps + itens). A recompensa de um streak N é a do dia {@code ((N-1) % cycle) + 1}.
 */
public class DailyRewardsConfig {

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;

    private int cycleDays = 7;
    private final Map<Integer, DayReward> rewards = new LinkedHashMap<>();

    public DailyRewardsConfig(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    public void load() {
        rewards.clear();
        FileConfiguration c = configManager.loadConfig("daily-rewards.yml");
        this.cycleDays = Math.max(1, c.getInt("cycle-days", 7));
        if (cycleDays > 7) {
            // O menu (DailyMenu) só tem 7 slots de dia; acima disso a régua visual quebra.
            plugin.getLogger().warning("daily-rewards.yml: cycle-days > 7 não é suportado pelo menu; limitando a 7.");
            this.cycleDays = 7;
        }
        ConfigurationSection rs = c.getConfigurationSection("rewards");
        if (rs == null) {
            return;
        }
        for (String key : rs.getKeys(false)) {
            int day;
            try {
                day = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("daily-rewards.yml: dia inválido '" + key + "'.");
                continue;
            }
            ConfigurationSection daySec = rs.getConfigurationSection(key);
            if (daySec == null) {
                continue;
            }
            long scraps = Math.max(0, daySec.getLong("scraps", 0));
            List<ItemEntry> items = new ArrayList<>();
            for (Map<?, ?> raw : daySec.getMapList("items")) {
                ItemEntry e = parseItem(raw);
                if (e != null) {
                    items.add(e);
                }
            }
            rewards.put(day, new DayReward(day, scraps, items));
        }
    }

    public int cycleDays() {
        return cycleDays;
    }

    /** Dia do ciclo (1..cycleDays) correspondente a um streak. */
    public int rewardDayFor(long streak) {
        if (streak <= 0) {
            return 1;
        }
        return (int) ((streak - 1) % cycleDays) + 1;
    }

    /** Recompensa de um dia do ciclo (1..cycleDays), ou uma vazia se não configurada. */
    public DayReward rewardForDay(int day) {
        DayReward r = rewards.get(day);
        return r != null ? r : new DayReward(day, 0, List.of());
    }

    /** Recompensa correspondente a um streak. */
    public DayReward rewardForStreak(long streak) {
        return rewardForDay(rewardDayFor(streak));
    }

    /** Constrói o ItemStack de uma entrada (item Deadzone ou material vanilla), ou null se inválida. */
    public ItemStack build(ItemEntry e) {
        if (e.itemId() != null) {
            CustomItem ci = plugin.getItemRegistry().get(e.itemId());
            if (ci == null) {
                plugin.getLogger().warning("daily-rewards.yml: item '" + e.itemId() + "' não registrado.");
                return null;
            }
            ItemStack s = ci.build();
            s.setAmount(Math.max(1, Math.min(e.amount(), s.getMaxStackSize())));
            return s;
        }
        if (e.material() != null) {
            return new ItemStack(e.material(), Math.max(1, e.amount()));
        }
        return null;
    }

    private ItemEntry parseItem(Map<?, ?> raw) {
        Object itemObj = raw.get("item");
        Object matObj = raw.get("material");
        int amount = raw.get("amount") instanceof Number n ? n.intValue() : 1;
        if (itemObj != null) {
            return new ItemEntry(itemObj.toString(), null, Math.max(1, amount));
        }
        if (matObj != null) {
            Material m = Material.matchMaterial(matObj.toString());
            if (m == null) {
                plugin.getLogger().warning("daily-rewards.yml: material inválido '" + matObj + "'.");
                return null;
            }
            return new ItemEntry(null, m, Math.max(1, amount));
        }
        return null;
    }

    /** Recompensa de um dia do ciclo: scraps + itens. */
    public record DayReward(int day, long scraps, List<ItemEntry> items) {
    }

    /** Um item da recompensa: id Deadzone OU material vanilla, com quantidade. */
    public record ItemEntry(String itemId, Material material, int amount) {
    }
}
