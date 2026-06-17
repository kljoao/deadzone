package com.deadzone.modules.claim;

import com.deadzone.DeadzonePlugin;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Gerencia os baús físicos trancados por senha (posição -> tranca), com persistência. */
public class LockedChestManager {

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final DeadzonePlugin plugin;
    private final ClaimManager claims;
    private final File file;
    private final Map<String, LockedChest> locks = new ConcurrentHashMap<>();
    // Baús recém-colocados aguardando o dono definir o PIN: ninguém abre nesse intervalo.
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    public LockedChestManager(DeadzonePlugin plugin, ClaimManager claims) {
        this.plugin = plugin;
        this.claims = claims;
        this.file = new File(plugin.getDataFolder(), "lockedchests.yml");
        load();
    }

    private static String key(World w, int x, int y, int z) {
        return w.getName() + ":" + x + ":" + y + ":" + z;
    }

    private static String key(Block b) {
        return key(b.getWorld(), b.getX(), b.getY(), b.getZ());
    }

    public static boolean isChest(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST;
    }

    public boolean isLocked(Block block) {
        return locks.containsKey(key(block));
    }

    public void markPending(Block block) {
        pending.add(key(block));
    }

    public void clearPending(Block block) {
        pending.remove(key(block));
    }

    public boolean isPending(Block block) {
        return pending.contains(key(block));
    }

    public LockedChest getLock(Block block) {
        return locks.get(key(block));
    }

    /** Cria uma tranca nova com PIN (guarda só o hash). */
    public LockedChest lockNew(Block block, UUID owner, String pin) {
        LockedChest lc = LockedChest.create(owner, block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(), pin, new HashSet<>());
        locks.put(key(block), lc);
        save();
        return lc;
    }

    /** Vincula uma posição a uma tranca existente (baú duplo): compartilha hash/salt e autorizados. */
    public void inherit(Block block, LockedChest source) {
        LockedChest lc = new LockedChest(source.owner(), block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(), source.pinHash(), source.salt(), source.authorized());
        locks.put(key(block), lc);
        save();
    }

    public void removeLock(Block block) {
        if (locks.remove(key(block)) != null) {
            save();
        }
    }

    public void authorize(Block block, UUID uuid) {
        LockedChest lc = getLock(block);
        if (lc != null && lc.authorized().add(uuid)) {
            save();
        }
    }

    /** Tranca de um baú vizinho (horizontal) do mesmo dono, para herdar PIN ao formar baú duplo. */
    public LockedChest adjacentLockOfOwner(Block block, UUID owner) {
        for (BlockFace side : SIDES) {
            Block rel = block.getRelative(side);
            if (isChest(rel.getType())) {
                LockedChest lc = getLock(rel);
                if (lc != null && lc.owner().equals(owner)) {
                    return lc;
                }
            }
        }
        return null;
    }

    /** Pode abrir sem PIN? Dono do baú, autorizado, ou dono/membro da base com permissão de baús. */
    public boolean canOpen(Player player, Block block) {
        LockedChest lc = getLock(block);
        if (lc == null) {
            return true;
        }
        return lc.owner().equals(player.getUniqueId())
                || lc.isAuthorized(player.getUniqueId())
                || claims.canContainers(player, block.getLocation());
    }

    /** Quantos blocos de baú trancado existem dentro de um claim (para o limite). */
    public int countInClaim(Claim claim) {
        int count = 0;
        for (LockedChest lc : locks.values()) {
            if (lc.world().equals(claim.world()) && claim.contains(lc.x(), lc.y(), lc.z())) {
                count++;
            }
        }
        return count;
    }

    /** Revoga a autorização de um jogador em todos os baús de um claim (ex.: membro removido da base). */
    public void revokeInClaim(Claim claim, UUID uuid) {
        boolean changed = false;
        for (LockedChest lc : locks.values()) {
            if (lc.world().equals(claim.world()) && claim.contains(lc.x(), lc.y(), lc.z())) {
                changed |= lc.authorized().remove(uuid);
            }
        }
        if (changed) {
            save();
        }
    }

    /** Remove todas as trancas dentro de um claim (ao remover a base). */
    public void removeLocksInClaim(Claim claim) {
        boolean changed = locks.values().removeIf(lc ->
                lc.world().equals(claim.world()) && claim.contains(lc.x(), lc.y(), lc.z()));
        if (changed) {
            save();
        }
    }

    // ----- persistência -----

    private void load() {
        locks.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("chests");
        if (root == null) {
            return;
        }
        for (String idx : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(idx);
            if (s == null) {
                continue;
            }
            try {
                String world = s.getString("world");
                UUID owner = UUID.fromString(s.getString("owner"));
                Set<UUID> auth = new HashSet<>();
                for (String a : s.getStringList("authorized")) {
                    try {
                        auth.add(UUID.fromString(a));
                    } catch (IllegalArgumentException ignored) {
                        // autorizado inválido
                    }
                }
                int x = s.getInt("x");
                int y = s.getInt("y");
                int z = s.getInt("z");
                String pinHash = s.getString("pin-hash");
                LockedChest lc;
                if (pinHash != null) {
                    lc = new LockedChest(owner, world, x, y, z, pinHash, s.getString("salt"), auth);
                } else {
                    // Migração: baú antigo guardava o PIN em texto puro -> converte para hash.
                    lc = LockedChest.create(owner, world, x, y, z, s.getString("pin", "0000"), auth);
                }
                locks.put(world + ":" + x + ":" + y + ":" + z, lc);
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // entrada inválida
            }
        }
    }

    private void save() {
        FileConfiguration cfg = new YamlConfiguration();
        int i = 0;
        for (LockedChest lc : locks.values()) {
            String base = "chests." + i++;
            cfg.set(base + ".world", lc.world());
            cfg.set(base + ".x", lc.x());
            cfg.set(base + ".y", lc.y());
            cfg.set(base + ".z", lc.z());
            cfg.set(base + ".owner", lc.owner().toString());
            cfg.set(base + ".pin-hash", lc.pinHash());
            cfg.set(base + ".salt", lc.salt());
            List<String> auth = new ArrayList<>();
            for (UUID a : lc.authorized()) {
                auth.add(a.toString());
            }
            cfg.set(base + ".authorized", auth);
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Falha ao salvar lockedchests.yml: " + ex.getMessage());
        }
    }
}
