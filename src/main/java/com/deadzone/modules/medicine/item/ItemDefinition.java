package com.deadzone.modules.medicine.item;

import com.deadzone.core.item.Tier;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/** Aparência e metadados de um item customizado, carregados do items.yml. */
public record ItemDefinition(
        String id,
        Material material,
        String name,          // MiniMessage
        List<String> lore,    // MiniMessage por linha
        int modelData,
        Tier tier,
        String requiredSkill, // null = craftável por todos
        Map<String, Object> use,
        Map<String, Integer> recipe // chave = id de item custom OU nome de Material
) {

    public boolean hasRecipe() {
        return recipe != null && !recipe.isEmpty();
    }

    public int useInt(String key, int def) {
        Object o = use.get(key);
        return (o instanceof Number n) ? n.intValue() : def;
    }

    public double useDouble(String key, double def) {
        Object o = use.get(key);
        return (o instanceof Number n) ? n.doubleValue() : def;
    }

    public int cooldownSeconds() {
        return useInt("cooldown-seconds", 0);
    }
}
