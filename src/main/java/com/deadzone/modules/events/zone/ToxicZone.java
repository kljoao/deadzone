package com.deadzone.modules.events.zone;

import org.bukkit.Location;

import java.util.List;

/**
 * Uma zona tóxica/irradiada (cuboide). Causa dano por segundo a quem não estiver protegido.
 */
public record ToxicZone(
        String name,
        String world,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        double damagePerSecond,
        String protectionItemId,   // id do item custom que protege (ex.: gas_mask)
        List<String> effects       // ids de efeitos minecraft (ex.: poison, nausea)
) {

    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
