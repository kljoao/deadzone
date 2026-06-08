package com.deadzone.modules.infection;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.entity.EntityKeys;
import com.deadzone.core.profile.PlayerClass;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.core.scheduler.TickService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Núcleo do sistema de infecção. Sem cura: infectou, é contagem regressiva. */
public class InfectionManager {

    private static final String SKILL_VIRAL_RESISTANCE = "bru_resistencia_viral";
    private static final String RUNNER_TYPE = "RUNNER";

    private final DeadzonePlugin plugin;
    private final InfectionConfig config;

    /** Última faixa de infecção conhecida por jogador, p/ detectar transições. */
    private final Map<UUID, Integer> lastStage = new HashMap<>();

    /** Jogadores na sequência de colapso (100% atingido), aguardando a morte. */
    private final Map<UUID, BukkitTask> deathSequences = new HashMap<>();

    /** Supressão temporária de sintomas (Antídoto): uuid -> expira em millis. */
    private final Map<UUID, Long> symptomsSuppressedUntil = new HashMap<>();

    public InfectionManager(DeadzonePlugin plugin, InfectionConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void enable(TickService tickService) {
        tickService.registerSecondHandler(this::tick);
        plugin.getServer().getPluginManager().registerEvents(new InfectionListener(plugin, this), plugin);
    }

    public InfectionConfig config() {
        return config;
    }

    // tick: 1x/segundo por jogador online
    public void tick(PlayerProfile p) {
        if (!p.isInfected()) {
            lastStage.remove(p.getUuid());
            return;
        }
        double now = Math.min(100.0, p.getInfectionLevel() + config.incrementPerSecond());
        p.setInfectionLevel(now);

        Player player = Bukkit.getPlayer(p.getUuid());
        if (player != null) {
            if (!isSymptomsSuppressed(p.getUuid())) {
                applyStageEffects(player, now);
            }
            notifyStageTransition(player, now);
            ambientFeedback(player, now);
            if (config.showActionBar()) {
                player.sendActionBar(actionBar(now));
            }
        }

        if (now >= 100.0) {
            triggerDeath(p);
        }
    }

    public void onZombieDamage(Player victim, PlayerProfile p, Entity damager) {
        if (p.isInfected()) {
            return; // já infectado: agravamento é tratado em onDamageWhileInfected
        }
        double chance;
        if (isRunner(damager)) {
            chance = config.runnerChance(); // 5% fixo, ignora Resistência Viral
        } else {
            chance = applyViralResistance(p, config.baseChance());
        }
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            infect(p, victim);
        }
    }

    public void onDamageWhileInfected(PlayerProfile p, DamageCause cause) {
        if (!p.isInfected()) {
            return;
        }
        if (!config.aggravateCauses().contains(cause)) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= config.aggravateChance()) {
            return;
        }
        double now = Math.min(100.0, p.getInfectionLevel() + config.aggravateAmount());
        p.setInfectionLevel(now);

        Player player = Bukkit.getPlayer(p.getUuid());
        if (player != null) {
            player.playSound(player, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.6f, 0.5f);
            player.sendActionBar(Component.text("A infecção avança...", NamedTextColor.DARK_RED));
        }
        if (now >= 100.0) {
            triggerDeath(p);
        }
    }

    public void infect(PlayerProfile p, Player player) {
        if (p.isInfected()) {
            return;
        }
        p.setInfected(true);
        p.setInfectionLevel(0.0);
        lastStage.put(p.getUuid(), -1);
        if (player != null) {
            player.playSound(player, Sound.ENTITY_ZOMBIE_INFECT, 1.0f, 0.8f);
            player.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text("INFECTADO", NamedTextColor.DARK_RED),
                    Component.text("Não há cura. É só questão de tempo.", NamedTextColor.RED)));
        }
    }

    /** Define o nível manualmente (admin/debug). 0 com infected=false equivale a "limpar". */
    public void setLevel(PlayerProfile p, double level) {
        if (level <= 0) {
            p.setInfected(false);
            p.setInfectionLevel(0.0);
            lastStage.remove(p.getUuid());
            cancelCollapse(p.getUuid());
        } else {
            p.setInfected(true);
            p.setInfectionLevel(level);
        }
    }

    /**
     * Decide o que acontece ao chegar a 100%: inicia a sequência de colapso (se habilitada
     * e o jogador estiver online) ou mata imediatamente.
     */
    private void triggerDeath(PlayerProfile p) {
        Player player = Bukkit.getPlayer(p.getUuid());
        if (config.collapseEnabled() && player != null) {
            startCollapse(player);
        } else {
            killByInfection(p);
        }
    }

    /** Sequência de colapso: cegueira/lentidão + título na tela, morte após duration-seconds. */
    private void startCollapse(Player player) {
        UUID uuid = player.getUniqueId();
        if (deathSequences.containsKey(uuid)) {
            return; // já colapsando
        }
        int secs = config.collapseSeconds();
        int durationTicks = secs * 20 + 40;

        if (config.collapseBlindness()) {
            PotionEffectType blindness = Registry.EFFECT.get(NamespacedKey.minecraft("blindness"));
            if (blindness != null) {
                player.addPotionEffect(new PotionEffect(blindness, durationTicks, 0, true, false, false));
            }
        }
        if (config.collapseSlownessAmp() >= 0) {
            PotionEffectType slowness = Registry.EFFECT.get(NamespacedKey.minecraft("slowness"));
            if (slowness != null) {
                player.addPotionEffect(new PotionEffect(slowness, durationTicks, config.collapseSlownessAmp(), true, false, false));
            }
        }

        player.playSound(player, Sound.ENTITY_PLAYER_HURT, 0.8f, 0.6f);
        player.showTitle(Title.title(
                plugin.getMessages().parse(config.collapseTitle()),
                plugin.getMessages().parse(config.collapseSubtitle()),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(secs), Duration.ofMillis(700))));

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            deathSequences.remove(uuid);
            PlayerProfile cur = plugin.getProfileManager().get(uuid);
            Player pl = Bukkit.getPlayer(uuid);
            if (cur != null && pl != null && cur.isInfected() && cur.getInfectionLevel() >= 100.0) {
                killByInfection(cur);
            }
        }, secs * 20L);

        deathSequences.put(uuid, task);
    }

    private void cancelCollapse(UUID uuid) {
        BukkitTask task = deathSequences.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void killByInfection(PlayerProfile p) {
        p.setDyingFromInfection(true);
        lastStage.remove(p.getUuid());
        cancelCollapse(p.getUuid());
        Player player = Bukkit.getPlayer(p.getUuid());
        if (player != null) {
            player.setHealth(0.0); // dispara PlayerDeathEvent -> wipe total
        }
    }

    /** Suprime os sintomas (efeitos de estágio) por alguns segundos. Não afeta o medidor. */
    public void suppressSymptoms(UUID uuid, int seconds) {
        symptomsSuppressedUntil.put(uuid, System.currentTimeMillis() + seconds * 1000L);
    }

    private boolean isSymptomsSuppressed(UUID uuid) {
        Long until = symptomsSuppressedUntil.get(uuid);
        return until != null && System.currentTimeMillis() < until;
    }

    public double applyViralResistance(PlayerProfile p, double chance) {
        if (p.getPlayerClass() == PlayerClass.BRUTO && p.hasSkill(SKILL_VIRAL_RESISTANCE)) {
            return switch (config.resistanceMode()) {
                case RELATIVE -> chance * (1.0 - config.resistanceAmount());
                case ABSOLUTE -> Math.max(0.0, chance - config.resistanceAmount());
            };
        }
        return chance;
    }

    public boolean isZombieType(Entity entity) {
        return config.zombieTypes().contains(entity.getType());
    }

    private boolean isRunner(Entity entity) {
        if (EntityKeys.ZOMBIE_TYPE == null) {
            return false;
        }
        String type = entity.getPersistentDataContainer().get(EntityKeys.ZOMBIE_TYPE, PersistentDataType.STRING);
        return RUNNER_TYPE.equalsIgnoreCase(type);
    }

    private void applyStageEffects(Player player, double level) {
        InfectionConfig.Stage stage = stageFor(level);
        if (stage == null) {
            return;
        }
        for (InfectionConfig.EffectSpec e : stage.effects()) {
            if (e.chance() >= 1.0) {
                // contínuo: reaplicado a cada segundo com duração curta (expira sozinho se a infecção sumir)
                player.addPotionEffect(new PotionEffect(e.type(), 60, e.amplifier(), true, false, false));
            } else if (ThreadLocalRandom.current().nextDouble() < e.chance()) {
                player.addPotionEffect(new PotionEffect(e.type(), e.durationSeconds() * 20, e.amplifier(), true, false, false));
            }
        }
    }

    private void notifyStageTransition(Player player, double level) {
        int idx = stageIndexFor(level);
        Integer prev = lastStage.get(player.getUniqueId());
        lastStage.put(player.getUniqueId(), idx);
        if (idx >= 0 && (prev == null || idx > prev)) {
            player.playSound(player, Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.7f);
            player.sendMessage(Component.text("Você sente a infecção se agravando...", NamedTextColor.DARK_RED));
        }
    }

    private void ambientFeedback(Player player, double level) {
        if (level >= 75 && ThreadLocalRandom.current().nextDouble() < 0.10) {
            player.playSound(player, Sound.ENTITY_WARDEN_HEARTBEAT, 0.5f, 0.6f);
        }
    }

    private Component actionBar(double level) {
        NamedTextColor color = level >= 75 ? NamedTextColor.DARK_RED
                : level >= 50 ? NamedTextColor.RED
                : level >= 25 ? NamedTextColor.GOLD
                : NamedTextColor.YELLOW;
        return Component.text("☣ Infecção: " + String.format("%.0f", level) + "%", color);
    }

    private InfectionConfig.Stage stageFor(double level) {
        int idx = stageIndexFor(level);
        return idx >= 0 ? config.stages().get(idx) : null;
    }

    /** Índice da faixa ativa (maior 'from' <= level), ou -1 se nenhuma. */
    private int stageIndexFor(double level) {
        int idx = -1;
        var stages = config.stages();
        for (int i = 0; i < stages.size(); i++) {
            if (level >= stages.get(i).from()) {
                idx = i;
            } else {
                break;
            }
        }
        return idx;
    }
}
