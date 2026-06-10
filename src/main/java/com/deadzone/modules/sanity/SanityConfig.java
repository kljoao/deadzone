package com.deadzone.modules.sanity;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Acesso tipado ao sanity.yml. */
public class SanityConfig {

    public record EffectTier(double below, double meleeMultiplier, int slowness, boolean visual) {
    }

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;

    private double start;
    private int lightThreshold;
    private Set<Material> lightItems;

    private double darknessLoss;
    private int zombieRadius;
    private int zombieThreshold;
    private double perZombieLoss;

    private double litAreaGain;
    private double baseBonus;
    private int baseRadius;
    private double companyGain;
    private int companyRadius;
    private double daylightGain;

    private List<EffectTier> effects;
    private double bossbarShowBelow;

    private int traumaWitnessRadius;
    private double traumaWitnessLoss;
    private double traumaKillLoss;
    private int traumaKillThreshold;

    public SanityConfig(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    public void load() {
        FileConfiguration c = configManager.loadConfig("sanity.yml");

        this.start = c.getDouble("start", 100);
        this.lightThreshold = c.getInt("light-threshold", 8);

        this.lightItems = EnumSet.noneOf(Material.class);
        for (String name : c.getStringList("light-items")) {
            Material m = Material.matchMaterial(name);
            if (m != null) {
                lightItems.add(m);
            }
        }

        this.darknessLoss = c.getDouble("loss.darkness", -0.15);
        this.zombieRadius = c.getInt("loss.zombie-pressure.radius", 12);
        this.zombieThreshold = c.getInt("loss.zombie-pressure.threshold", 3);
        this.perZombieLoss = c.getDouble("loss.zombie-pressure.per-zombie", -0.05);

        this.litAreaGain = c.getDouble("recovery.lit-area", 0.15);
        this.baseBonus = c.getDouble("recovery.base-bonus", 0.25);
        this.baseRadius = c.getInt("recovery.base-radius", 12);
        this.companyGain = c.getDouble("recovery.company", 0.10);
        this.companyRadius = c.getInt("recovery.company-radius", 16);
        this.daylightGain = c.getDouble("recovery.daylight", 0.05);

        this.effects = new ArrayList<>();
        for (Map<?, ?> map : c.getMapList("effects")) {
            double below = toDouble(map.get("below"), 0);
            double melee = toDouble(map.get("melee-multiplier"), 1.0);
            int slowness = (int) toDouble(map.get("slowness"), -1);
            boolean visual = Boolean.TRUE.equals(map.get("visual"));
            effects.add(new EffectTier(below, melee, slowness, visual));
        }
        // mais severo primeiro (menor 'below')
        effects.sort((a, b) -> Double.compare(a.below(), b.below()));

        this.bossbarShowBelow = c.getDouble("bossbar.show-below", 75);

        this.traumaWitnessRadius = c.getInt("trauma.witness-radius", 16);
        this.traumaWitnessLoss = c.getDouble("trauma.witness-loss", 8);
        this.traumaKillLoss = c.getDouble("trauma.kill-loss", 15);
        this.traumaKillThreshold = c.getInt("trauma.kill-threshold", 3);
    }

    private double toDouble(Object o, double def) {
        return (o instanceof Number n) ? n.doubleValue() : def;
    }

    /** Faixa de efeito ativa para uma dada sanidade (a mais severa), ou null se saudável. */
    public EffectTier tierFor(double sanity) {
        for (EffectTier tier : effects) { // ordenado do mais severo p/ o menos
            if (sanity < tier.below()) {
                return tier;
            }
        }
        return null;
    }

    public double meleeMultiplier(double sanity) {
        EffectTier tier = tierFor(sanity);
        return tier != null ? tier.meleeMultiplier() : 1.0;
    }

    public double start() { return start; }
    public int lightThreshold() { return lightThreshold; }
    public Set<Material> lightItems() { return lightItems; }
    public double darknessLoss() { return darknessLoss; }
    public int zombieRadius() { return zombieRadius; }
    public int zombieThreshold() { return zombieThreshold; }
    public double perZombieLoss() { return perZombieLoss; }
    public double litAreaGain() { return litAreaGain; }
    public double baseBonus() { return baseBonus; }
    public int baseRadius() { return baseRadius; }
    public double companyGain() { return companyGain; }
    public int companyRadius() { return companyRadius; }
    public double daylightGain() { return daylightGain; }
    public double bossbarShowBelow() { return bossbarShowBelow; }
    public int traumaWitnessRadius() { return traumaWitnessRadius; }
    public double traumaWitnessLoss() { return traumaWitnessLoss; }
    public double traumaKillLoss() { return traumaKillLoss; }
    public int traumaKillThreshold() { return traumaKillThreshold; }
}
