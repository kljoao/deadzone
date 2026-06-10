package com.deadzone.modules.claim;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Uma base reivindicada: caixa 3D, núcleo no centro, e membros com permissões. */
public final class Claim {

    public static final String FLAG_BUILD = "build";
    public static final String FLAG_CONTAINERS = "containers";
    public static final String FLAG_DOORS = "doors";

    private final UUID owner;
    private final String world;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int minY;
    private int maxY; // mutável (upgrade de altura, Fase 3)
    private final int nucleoX;
    private final int nucleoY;
    private final int nucleoZ;
    private final Map<UUID, Set<String>> members = new HashMap<>();
    private int heightUpgrades;
    private int chestUpgrades;

    public Claim(UUID owner, String world, int minX, int maxX, int minZ, int maxZ, int minY, int maxY,
                 int nucleoX, int nucleoY, int nucleoZ) {
        this.owner = owner;
        this.world = world;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.minY = minY;
        this.maxY = maxY;
        this.nucleoX = nucleoX;
        this.nucleoY = nucleoY;
        this.nucleoZ = nucleoZ;
    }

    public UUID owner() {
        return owner;
    }

    public String world() {
        return world;
    }

    public int minX() {
        return minX;
    }

    public int maxX() {
        return maxX;
    }

    public int minZ() {
        return minZ;
    }

    public int maxZ() {
        return maxZ;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public void setMaxY(int maxY) {
        this.maxY = maxY;
    }

    public int nucleoX() {
        return nucleoX;
    }

    public int nucleoY() {
        return nucleoY;
    }

    public int nucleoZ() {
        return nucleoZ;
    }

    public int heightUpgrades() {
        return heightUpgrades;
    }

    public void setHeightUpgrades(int v) {
        this.heightUpgrades = v;
    }

    public int chestUpgrades() {
        return chestUpgrades;
    }

    public void setChestUpgrades(int v) {
        this.chestUpgrades = v;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY && y <= maxY;
    }

    public boolean overlaps2D(int aMinX, int aMaxX, int aMinZ, int aMaxZ) {
        return minX <= aMaxX && maxX >= aMinX && minZ <= aMaxZ && maxZ >= aMinZ;
    }

    public boolean isNucleo(int x, int y, int z) {
        return x == nucleoX && y == nucleoY && z == nucleoZ;
    }

    // ----- membros -----

    public Map<UUID, Set<String>> members() {
        return members;
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public boolean hasFlag(UUID uuid, String flag) {
        Set<String> flags = members.get(uuid);
        return flags != null && flags.contains(flag);
    }

    public void addMember(UUID uuid) {
        members.computeIfAbsent(uuid, k -> new HashSet<>());
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public void setFlag(UUID uuid, String flag, boolean on) {
        Set<String> flags = members.computeIfAbsent(uuid, k -> new HashSet<>());
        if (on) {
            flags.add(flag);
        } else {
            flags.remove(flag);
        }
    }
}
