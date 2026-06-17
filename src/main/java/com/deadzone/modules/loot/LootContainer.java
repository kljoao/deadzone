package com.deadzone.modules.loot;

import org.bukkit.block.Block;

/** Um baú de loot registrado no mundo (posição + tipo de caixa). A posição é a identidade. */
public record LootContainer(String world, int x, int y, int z, String type) {

    public static String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public static String key(Block b) {
        return key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    public String key() {
        return key(world, x, y, z);
    }
}
