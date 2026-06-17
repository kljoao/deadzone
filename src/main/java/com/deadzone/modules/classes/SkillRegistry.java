package com.deadzone.modules.classes;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.profile.PlayerClass;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carrega os {@link SkillNode} de classes.yml.
 */
public class SkillRegistry {

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;

    private Map<String, SkillNode> nodes = new LinkedHashMap<>();

    public SkillRegistry(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    public void load() {
        Map<String, SkillNode> map = new LinkedHashMap<>();
        ConfigurationSection skills = configManager.loadConfig("classes.yml").getConfigurationSection("skills");
        if (skills != null) {
            for (String id : skills.getKeys(false)) {
                ConfigurationSection s = skills.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                PlayerClass clazz = PlayerClass.fromString(s.getString("class", "NONE"));
                Material icon = Material.matchMaterial(s.getString("icon", "PAPER"));
                if (icon == null) {
                    icon = Material.PAPER;
                }
                SkillNode node = new SkillNode(
                        id,
                        s.getString("name", id),
                        clazz,
                        icon,
                        s.getLong("cost", 100),
                        s.getInt("slot", 0),
                        s.getStringList("prerequisites"),
                        s.getStringList("description"));
                map.put(id, node);
            }
        }
        this.nodes = map;
    }

    public SkillNode get(String id) {
        return nodes.get(id);
    }

    public List<SkillNode> nodesFor(PlayerClass clazz) {
        List<SkillNode> out = new ArrayList<>();
        for (SkillNode node : nodes.values()) {
            // class: NONE = skill UNIVERSAL (aparece na árvore de todas as classes).
            if (node.requiredClass() == clazz || node.requiredClass() == PlayerClass.NONE) {
                out.add(node);
            }
        }
        return out;
    }
}
