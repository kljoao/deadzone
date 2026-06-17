package com.deadzone.modules.medicine.item;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.item.Tier;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Carrega as ItemDefinition do items.yml. */
public class ItemsConfig {

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;

    private Map<String, ItemDefinition> definitions = new LinkedHashMap<>();

    public ItemsConfig(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    public void load() {
        Map<String, ItemDefinition> map = new LinkedHashMap<>();
        var c = configManager.loadConfig("items.yml");
        ConfigurationSection items = c.getConfigurationSection("items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                ItemDefinition def = parse(id, s);
                if (def != null) {
                    map.put(id, def);
                }
            }
        }
        this.definitions = map;
    }

    private ItemDefinition parse(String id, ConfigurationSection s) {
        Material material = Material.matchMaterial(s.getString("material", "PAPER"));
        if (material == null) {
            plugin.getLogger().warning("Material inválido no item '" + id + "'. Usando PAPER.");
            material = Material.PAPER;
        }
        String name = s.getString("name", id);
        List<String> lore = s.getStringList("lore");
        int modelData = s.getInt("model-data", 0);

        Tier tier;
        try {
            tier = Tier.valueOf(s.getString("tier", "T1").toUpperCase());
        } catch (IllegalArgumentException e) {
            tier = Tier.T1;
        }

        String requiredSkill = s.getString("recipe.requires-skill", null);
        if (requiredSkill != null && requiredSkill.isBlank()) {
            requiredSkill = null;
        }

        String category = s.getString("category", "medica").toLowerCase();

        Map<String, Object> use = new HashMap<>();
        ConfigurationSection useSec = s.getConfigurationSection("use");
        if (useSec != null) {
            use.putAll(useSec.getValues(false));
        }

        Map<String, Integer> recipe = new LinkedHashMap<>();
        ConfigurationSection ing = s.getConfigurationSection("recipe.ingredients");
        if (ing != null) {
            for (String key : ing.getKeys(false)) {
                recipe.put(key, ing.getInt(key));
            }
        }

        return new ItemDefinition(id, material, name, lore, modelData, tier, requiredSkill, category, use, recipe);
    }

    public ItemDefinition get(String id) {
        return definitions.get(id);
    }

    public List<ItemDefinition> all() {
        return new ArrayList<>(definitions.values());
    }
}
