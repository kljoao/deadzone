package com.deadzone.modules.infection;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Acesso tipado ao infection.yml. Recarregável. */
public class InfectionConfig {

    public enum ResistanceMode {RELATIVE, ABSOLUTE}

    /** Um efeito de potion aplicado numa faixa de infecção. */
    public record EffectSpec(PotionEffectType type, int amplifier, double chance, int durationSeconds) {
    }

    /** Uma faixa: a partir de {@code from}% aplicam-se estes efeitos. */
    public record Stage(double from, List<EffectSpec> effects) {
    }

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;

    private double baseChance;
    private double runnerChance;
    private double timeToDeathSeconds;
    private Set<EntityType> zombieTypes;

    private double aggravateChance;
    private double aggravateAmount;
    private Set<DamageCause> aggravateCauses;

    private ResistanceMode resistanceMode;
    private double resistanceAmount;

    private boolean showActionBar;

    private boolean collapseEnabled;
    private int collapseSeconds;
    private String collapseTitle;
    private String collapseSubtitle;
    private boolean collapseBlindness;
    private int collapseSlownessAmp;

    private List<Stage> stages; // ordenado por 'from' crescente

    public InfectionConfig(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    public void load() {
        FileConfiguration c = configManager.loadConfig("infection.yml");

        this.baseChance = c.getDouble("base-infection-chance", 0.25);
        this.runnerChance = c.getDouble("runner-infection-chance", 0.05);
        this.timeToDeathSeconds = Math.max(1.0, c.getDouble("time-to-death-seconds", 3600));

        this.zombieTypes = EnumSet.noneOf(EntityType.class);
        for (String name : c.getStringList("zombie-types")) {
            EntityType type = parseEnum(EntityType.class, name);
            if (type != null) {
                zombieTypes.add(type);
            }
        }
        if (zombieTypes.isEmpty()) {
            zombieTypes.addAll(EnumSet.of(EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER));
        }

        this.aggravateChance = c.getDouble("aggravate.chance", 0.50);
        this.aggravateAmount = c.getDouble("aggravate.amount", 15);
        this.aggravateCauses = EnumSet.noneOf(DamageCause.class);
        for (String name : c.getStringList("aggravate.count-causes")) {
            DamageCause cause = parseEnum(DamageCause.class, name);
            if (cause != null) {
                aggravateCauses.add(cause);
            }
        }
        if (aggravateCauses.isEmpty()) {
            aggravateCauses.addAll(EnumSet.of(DamageCause.ENTITY_ATTACK, DamageCause.ENTITY_SWEEP_ATTACK, DamageCause.PROJECTILE));
        }

        this.resistanceMode = "ABSOLUTE".equalsIgnoreCase(c.getString("viral-resistance.mode", "RELATIVE"))
                ? ResistanceMode.ABSOLUTE : ResistanceMode.RELATIVE;
        this.resistanceAmount = c.getDouble("viral-resistance.amount", 0.15);

        this.showActionBar = c.getBoolean("show-actionbar", false);

        this.collapseEnabled = c.getBoolean("collapse.enabled", true);
        this.collapseSeconds = Math.max(1, c.getInt("collapse.duration-seconds", 10));
        this.collapseTitle = c.getString("collapse.title", "<dark_red><bold>Não aguento mais...</bold>");
        this.collapseSubtitle = c.getString("collapse.subtitle", "");
        this.collapseBlindness = c.getBoolean("collapse.blindness", true);
        this.collapseSlownessAmp = c.getInt("collapse.slowness-amplifier", 4);

        this.stages = parseStages(c.getConfigurationSection("stages"));
    }

    private List<Stage> parseStages(ConfigurationSection section) {
        List<Stage> out = new ArrayList<>();
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            double from = s.getDouble("from", 0);
            List<EffectSpec> effects = new ArrayList<>();
            for (Map<?, ?> map : s.getMapList("effects")) {
                EffectSpec spec = parseEffect(map);
                if (spec != null) {
                    effects.add(spec);
                }
            }
            out.add(new Stage(from, effects));
        }
        out.sort((a, b) -> Double.compare(a.from(), b.from()));
        return out;
    }

    private EffectSpec parseEffect(Map<?, ?> map) {
        Object typeObj = map.get("type");
        if (typeObj == null) {
            return null;
        }
        PotionEffectType type = resolveEffect(typeObj.toString());
        if (type == null) {
            plugin.getLogger().warning("Efeito de infecção desconhecido: " + typeObj);
            return null;
        }
        int amplifier = toInt(map.get("amplifier"), 0);
        double chance = toDouble(map.get("chance"), 1.0);
        int duration = toInt(map.get("duration"), 3);
        return new EffectSpec(type, amplifier, chance, duration);
    }

    private PotionEffectType resolveEffect(String name) {
        NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase().trim());
        return Registry.EFFECT.get(key);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String name) {
        if (name == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, name.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Valor inválido em infection.yml: " + name);
            return null;
        }
    }

    private int toInt(Object o, int def) {
        return (o instanceof Number n) ? n.intValue() : def;
    }

    private double toDouble(Object o, double def) {
        return (o instanceof Number n) ? n.doubleValue() : def;
    }

    public double baseChance() {
        return baseChance;
    }

    public double runnerChance() {
        return runnerChance;
    }

    public double incrementPerSecond() {
        return 100.0 / timeToDeathSeconds;
    }

    public Set<EntityType> zombieTypes() {
        return zombieTypes;
    }

    public double aggravateChance() {
        return aggravateChance;
    }

    public double aggravateAmount() {
        return aggravateAmount;
    }

    public Set<DamageCause> aggravateCauses() {
        return aggravateCauses;
    }

    public ResistanceMode resistanceMode() {
        return resistanceMode;
    }

    public double resistanceAmount() {
        return resistanceAmount;
    }

    public boolean showActionBar() {
        return showActionBar;
    }

    public boolean collapseEnabled() {
        return collapseEnabled;
    }

    public int collapseSeconds() {
        return collapseSeconds;
    }

    public String collapseTitle() {
        return collapseTitle;
    }

    public String collapseSubtitle() {
        return collapseSubtitle;
    }

    public boolean collapseBlindness() {
        return collapseBlindness;
    }

    public int collapseSlownessAmp() {
        return collapseSlownessAmp;
    }

    public List<Stage> stages() {
        return stages;
    }
}
