package com.deadzone.modules.loot;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

/**
 * Loot pelo mundo. Registry dos baús persistido em loot-containers.yml (igual ao lockedchests.yml).
 * Conteúdo COMPARTILHADO por baú (sessão) e progresso de busca POR-JOGADOR — ambos em memória (MVP);
 * sobrevivem ao relog (estado server-side) mas não a um restart (persistência em DB fica p/ a fase full).
 */
public class LootManager {

    private final DeadzonePlugin plugin;
    private final LootTablesConfig tables;
    private final File file;

    private final Map<String, LootContainer> registry = new ConcurrentHashMap<>();
    private final Map<String, LootSession> sessions = new HashMap<>();       // key = posição
    private final Map<String, int[]> progress = new HashMap<>();             // "uuid:pos" -> [generation, revealed]

    public LootManager(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.tables = new LootTablesConfig(plugin, configManager);
        this.file = new File(plugin.getDataFolder(), "loot-containers.yml");
    }

    public void enable() {
        loadRegistry();
    }

    public void reload() {
        tables.load();
    }

    public int count() {
        return registry.size();
    }

    public java.util.Set<String> types() {
        return tables.types();
    }

    public boolean hasType(String type) {
        return tables.hasType(type);
    }

    // ----- registry -----

    public LootContainer getContainer(Block block) {
        return registry.get(LootContainer.key(block));
    }

    public boolean mark(Block block, String type) {
        LootContainer c = new LootContainer(block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(), type.toLowerCase(java.util.Locale.ROOT));
        registry.put(c.key(), c);
        sessions.remove(c.key()); // força re-roll na próxima abertura
        saveRegistry();
        return true;
    }

    public boolean unmark(Block block) {
        String key = LootContainer.key(block);
        if (registry.remove(key) == null) {
            return false;
        }
        sessions.remove(key);
        saveRegistry();
        return true;
    }

    // ----- busca -----

    public void openSearch(Player player, LootContainer container) {
        LootSession s = session(container);
        int revealed = revealedFor(player, container, s.generation);
        new LootSearchMenu(plugin, this, container, s, revealed).open(player);
    }

    /** Sessão compartilhada do baú, re-rolando se o cooldown expirou. */
    private LootSession session(LootContainer c) {
        String key = c.key();
        long now = System.currentTimeMillis();
        long cooldownMs = tables.cooldownSeconds() * 1000L;
        LootSession s = sessions.get(key);
        if (s == null || now - s.rolledAt > cooldownMs) {
            int gen = (s == null) ? 1 : s.generation + 1;
            List<ItemStack> rolled = tables.rollLoot(c.type());
            s = new LootSession(rolled.toArray(new ItemStack[0]), gen, now);
            sessions.put(key, s);
        }
        return s;
    }

    /** Pega (atomicamente, na main thread) o item do slot do pool compartilhado; null se já foi pego. */
    public ItemStack takeItem(LootContainer container, int slot) {
        LootSession s = sessions.get(container.key());
        if (s == null || slot < 0 || slot >= s.contents.length) {
            return null;
        }
        ItemStack item = s.contents[slot];
        if (item == null) {
            return null;
        }
        s.contents[slot] = null;
        return item;
    }

    public int revealedFor(Player p, LootContainer c, int generation) {
        int[] e = progress.get(p.getUniqueId() + ":" + c.key());
        if (e == null || e[0] != generation) {
            return 0;
        }
        return e[1];
    }

    public void saveProgress(Player p, LootContainer c, int revealed) {
        LootSession s = sessions.get(c.key());
        int gen = (s == null) ? 0 : s.generation;
        progress.put(p.getUniqueId() + ":" + c.key(), new int[]{gen, revealed});
    }

    // ----- persistência do registry (YAML) -----

    private void loadRegistry() {
        registry.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("containers");
        if (root == null) {
            return;
        }
        for (String idx : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(idx);
            if (s == null) {
                continue;
            }
            String type = s.getString("type");
            String world = s.getString("world");
            if (type == null || world == null) {
                continue;
            }
            LootContainer c = new LootContainer(world, s.getInt("x"), s.getInt("y"), s.getInt("z"), type);
            registry.put(c.key(), c);
        }
    }

    private void saveRegistry() {
        FileConfiguration cfg = new YamlConfiguration();
        int i = 0;
        for (LootContainer c : registry.values()) {
            String base = "containers." + i++;
            cfg.set(base + ".world", c.world());
            cfg.set(base + ".x", c.x());
            cfg.set(base + ".y", c.y());
            cfg.set(base + ".z", c.z());
            cfg.set(base + ".type", c.type());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Falha ao salvar loot-containers.yml: " + e.getMessage());
        }
    }
}
