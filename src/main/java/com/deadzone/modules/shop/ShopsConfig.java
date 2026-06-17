package com.deadzone.modules.shop;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Carrega as lojas (shops.yml). */
public class ShopsConfig {

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, Shop> shops = new LinkedHashMap<>();

    public ShopsConfig(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    public void load() {
        shops.clear();
        FileConfiguration c = configManager.loadConfig("shops.yml");
        for (String key : c.getKeys(false)) {
            ConfigurationSection s = c.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            boolean sell = "sell".equalsIgnoreCase(s.getString("mode", "buy"));
            String title = s.getString("title", key);
            List<ShopEntry> entries = new ArrayList<>();
            for (Map<?, ?> raw : s.getMapList("items")) {
                ShopEntry e = parse(raw);
                if (e != null) {
                    entries.add(e);
                }
            }
            shops.put(key.toLowerCase(Locale.ROOT), new Shop(key.toLowerCase(Locale.ROOT), title, sell, entries));
        }
    }

    private ShopEntry parse(Map<?, ?> raw) {
        Object itemObj = raw.get("item");
        Object matObj = raw.get("material");
        long price = raw.get("price") instanceof Number n ? n.longValue() : 0L;
        int amount = raw.get("amount") instanceof Number n ? n.intValue() : 1;
        if (price <= 0) {
            return null;
        }
        if (itemObj != null) {
            return new ShopEntry(itemObj.toString(), null, price, Math.max(1, amount));
        }
        if (matObj != null) {
            Material m = Material.matchMaterial(matObj.toString());
            if (m == null) {
                plugin.getLogger().warning("shops.yml: material inválido '" + matObj + "'.");
                return null;
            }
            return new ShopEntry(null, m, price, Math.max(1, amount));
        }
        return null;
    }

    public Shop shop(String key) {
        return key == null ? null : shops.get(key.toLowerCase(Locale.ROOT));
    }

    /** Uma loja: título, modo (compra/venda) e itens. */
    public record Shop(String key, String title, boolean sell, List<ShopEntry> items) {
    }

    /** Uma entrada da loja: item Deadzone OU material vanilla, preço e quantidade por compra. */
    public record ShopEntry(String itemId, Material material, long price, int amount) {
    }
}
