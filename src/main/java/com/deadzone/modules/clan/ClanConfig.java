package com.deadzone.modules.clan;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/** Knobs do módulo de clans (clans.yml), incluindo a tabela de tiers. */
public class ClanConfig {

    /** Permissão para escolher tag colorida (VIP+). */
    public static final String PERM_COLORED_TAG = "deadzone.clan.coloredtag";
    /** Permissão para já começar no tier máximo (Warlord/Overlord). */
    public static final String PERM_MAX_TIER = "deadzone.clan.maxtier";

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;

    private long createCost = 2000;
    private int nameMin = 3;
    private int nameMax = 16;
    private int tagMin = 2;
    private int tagMax = 5;
    private long inviteTtlMs = 120_000L;
    private String defaultColor = "WHITE";
    private List<String> allowedColors = List.of("WHITE");

    private final TreeMap<Integer, Tier> tiers = new TreeMap<>();

    public ClanConfig(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    public void load() {
        FileConfiguration c = configManager.loadConfig("clans.yml");
        this.createCost = Math.max(0, c.getLong("create-cost", 2000));
        this.nameMin = c.getInt("name.min-length", 3);
        this.nameMax = c.getInt("name.max-length", 16);
        this.tagMin = c.getInt("tag.min-length", 2);
        this.tagMax = c.getInt("tag.max-length", 5);
        this.inviteTtlMs = Math.max(10, c.getLong("invite-ttl-seconds", 120)) * 1000L;
        // default-color precisa ser uma cor real do Minecraft (vira tag MiniMessage nos menus).
        String dc = c.getString("default-color", "WHITE");
        this.defaultColor = isMinecraftColor(dc) ? dc.toUpperCase(Locale.ROOT) : "WHITE";
        List<String> colors = c.getStringList("allowed-colors");
        if (!colors.isEmpty()) {
            List<String> valid = new ArrayList<>();
            for (String s : colors) {
                if (isMinecraftColor(s)) {
                    valid.add(s.toUpperCase(Locale.ROOT));
                } else {
                    plugin.getLogger().warning("clans.yml: cor inválida ignorada (não é cor do Minecraft): " + s);
                }
            }
            if (!valid.isEmpty()) {
                this.allowedColors = valid;
            }
        }

        tiers.clear();
        ConfigurationSection ts = c.getConfigurationSection("tiers");
        if (ts != null) {
            for (String key : ts.getKeys(false)) {
                int level;
                try {
                    level = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    continue;
                }
                ConfigurationSection t = ts.getConfigurationSection(key);
                if (t == null) {
                    continue;
                }
                tiers.put(level, new Tier(level, Math.max(1, t.getInt("members", 8)),
                        Math.max(0, t.getLong("cost", 0))));
            }
        }
        if (tiers.isEmpty()) {
            plugin.getLogger().warning("clans.yml: nenhum tier carregado — usando padrão (tier 1 = 8 vagas).");
            tiers.put(1, new Tier(1, 8, 0));
        } else {
            plugin.getLogger().info("Clãs: " + tiers.size() + " tiers carregados (máx = tier "
                    + maxTier() + " com " + tierMembers(maxTier()) + " vagas).");
        }
    }

    public long createCost() {
        return createCost;
    }

    public int nameMin() {
        return nameMin;
    }

    public int nameMax() {
        return nameMax;
    }

    public int tagMin() {
        return tagMin;
    }

    public int tagMax() {
        return tagMax;
    }

    public long inviteTtlMs() {
        return inviteTtlMs;
    }

    public String defaultColor() {
        return defaultColor;
    }

    public List<String> allowedColors() {
        return allowedColors;
    }

    public boolean isColorAllowed(String color) {
        return color != null && allowedColors.contains(color.toUpperCase(Locale.ROOT));
    }

    /** true se o nome é uma cor nomeada válida do Minecraft (= tag MiniMessage válida nos menus). */
    private static boolean isMinecraftColor(String name) {
        return name != null && NamedTextColor.NAMES.value(name.toLowerCase(Locale.ROOT)) != null;
    }

    // ----- tiers -----

    public int maxTier() {
        return tiers.lastKey();
    }

    /** Tier de um nível (clampado para [1, maxTier]). */
    public Tier tier(int level) {
        if (level < 1) {
            level = 1;
        }
        if (level > maxTier()) {
            level = maxTier();
        }
        Tier t = tiers.get(level);
        if (t != null) {
            return t;
        }
        // nível sem entrada explícita: usa o maior tier <= level
        return tiers.floorEntry(level).getValue();
    }

    public int tierMembers(int level) {
        return tier(level).members();
    }

    /** Custo (scraps) para chegar ao tier informado. */
    public long tierCost(int level) {
        return tier(level).cost();
    }

    /** Tabela completa (para o GUI). */
    public TreeMap<Integer, Tier> tiers() {
        return tiers;
    }

    public record Tier(int level, int members, long cost) {
    }
}
