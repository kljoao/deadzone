package com.deadzone.modules.classes;

import com.deadzone.core.profile.PlayerClass;
import org.bukkit.Material;

import java.util.List;

/** Um nó da árvore de habilidades, carregado de classes.yml. */
public record SkillNode(
        String id,
        String name,
        PlayerClass requiredClass,
        Material icon,
        long cost,
        int slot,
        List<String> prerequisites,
        List<String> description
) {
}
