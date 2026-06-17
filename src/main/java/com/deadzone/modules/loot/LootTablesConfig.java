package com.deadzone.modules.loot;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tabelas de loot (loot-tables.yml). Modelo de CHANCE INDEPENDENTE: cada entrada de uma caixa tem
 * sua própria % de cair (as chances NÃO somam 100; um baú pode ter 0..N itens). Tipos de caixa são
 * livres (string), definidos no YAML — crie "comum", "rara", "militar"... à vontade.
 */
public class LootTablesConfig {

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;

    private long cooldownSeconds = 1800;
    private final Map<String, List<Entry>> tables = new LinkedHashMap<>();

    public LootTablesConfig(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    public long cooldownSeconds() {
        return cooldownSeconds;
    }

    public Set<String> types() {
        return tables.keySet();
    }

    public boolean hasType(String type) {
        return type != null && tables.containsKey(type.toLowerCase(Locale.ROOT));
    }

    public void load() {
        tables.clear();
        FileConfiguration c = configManager.loadConfig("loot-tables.yml");
        this.cooldownSeconds = Math.max(1, c.getLong("cooldown-seconds", 1800));
        ConfigurationSection types = c.getConfigurationSection("types");
        if (types == null) {
            return;
        }
        for (String typeName : types.getKeys(false)) {
            ConfigurationSection t = types.getConfigurationSection(typeName);
            if (t == null) {
                continue;
            }
            List<Entry> list = new ArrayList<>();
            for (Map<?, ?> raw : t.getMapList("entries")) {
                Entry e = parseEntry(raw);
                if (e != null) {
                    list.add(e);
                }
            }
            tables.put(typeName.toLowerCase(Locale.ROOT), list);
        }
    }

    /** Sorteia o conteúdo de um baú: cada entrada cai pela sua chance independente. */
    public List<ItemStack> rollLoot(String type) {
        List<ItemStack> out = new ArrayList<>();
        List<Entry> entries = type == null ? null : tables.get(type.toLowerCase(Locale.ROOT));
        if (entries == null) {
            return out;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (Entry e : entries) {
            if (rng.nextDouble() * 100.0 < e.chance) {
                int count = e.min >= e.max ? e.min : rng.nextInt(e.min, e.max + 1);
                ItemStack stack = build(e, count);
                if (stack != null) {
                    out.add(stack);
                }
            }
        }
        return out;
    }

    private ItemStack build(Entry e, int count) {
        if (e.itemId != null) {
            CustomItem ci = plugin.getItemRegistry().get(e.itemId);
            if (ci == null) {
                plugin.getLogger().warning("loot-tables.yml: item '" + e.itemId + "' não registrado.");
                return null;
            }
            ItemStack s = ci.build();
            s.setAmount(Math.max(1, Math.min(count, s.getMaxStackSize())));
            return s;
        }
        if (e.material != null) {
            return new ItemStack(e.material, Math.max(1, count));
        }
        return null;
    }

    private Entry parseEntry(Map<?, ?> raw) {
        Object itemObj = raw.get("item");
        Object matObj = raw.get("material");
        double chance = raw.get("chance") instanceof Number n ? n.doubleValue() : 0.0;
        int min = raw.get("min") instanceof Number n ? n.intValue() : 1;
        int max = raw.get("max") instanceof Number n ? n.intValue() : min;
        if (chance <= 0) {
            return null;
        }
        if (itemObj != null) {
            return new Entry(itemObj.toString(), null, chance, min, Math.max(min, max));
        }
        if (matObj != null) {
            Material m = Material.matchMaterial(matObj.toString());
            if (m == null) {
                plugin.getLogger().warning("loot-tables.yml: material inválido '" + matObj + "'.");
                return null;
            }
            return new Entry(null, m, chance, min, Math.max(min, max));
        }
        return null;
    }

    private record Entry(String itemId, Material material, double chance, int min, int max) {
    }
}
