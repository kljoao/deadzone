package com.deadzone.modules.firearms;

import org.bukkit.Material;

import java.util.List;

/** Munição de uma arma (item que o jogador carrega e usa na recarga). */
public record AmmoType(
        String id,
        Material material,
        int customModelData,
        String displayName,
        List<String> lore) {
}
