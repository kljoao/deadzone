package com.deadzone.modules.classes;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.profile.PlayerClass;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.core.scheduler.TickService;
import com.deadzone.modules.classes.downed.DownedListener;
import com.deadzone.modules.classes.downed.DownedManager;
import com.deadzone.modules.classes.saqueador.PeDeCabra;
import com.deadzone.modules.classes.saqueador.RadioFrequencia;
import com.deadzone.modules.classes.saqueador.SaqueadorListener;
import com.deadzone.modules.classes.saqueador.SaqueadorManager;
import com.deadzone.modules.classes.skills.StunListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Orquestra o módulo de classes: seleção, XP, árvore de habilidades, derrubado/revive e skills. */
public class ClassManager {

    public enum UnlockResult {SUCCESS, ALREADY, WRONG_CLASS, LOCKED, NO_XP}

    private static final String SKILL_DIAGNOSIS = "med_diagnostico_rapido";

    private final DeadzonePlugin plugin;
    private final ClassConfig config;
    private final SkillRegistry skills;
    private final DownedManager downedManager;
    private final SaqueadorManager saqueadorManager;

    public ClassManager(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.config = new ClassConfig(plugin, configManager);
        this.skills = new SkillRegistry(plugin, configManager);
        this.downedManager = new DownedManager(plugin, config);
        this.saqueadorManager = new SaqueadorManager(plugin, config);
    }

    public void enable(TickService tickService) {
        var pm = plugin.getServer().getPluginManager();
        pm.registerEvents(new XpListener(plugin, this), plugin);
        pm.registerEvents(new StunListener(plugin), plugin);
        pm.registerEvents(new DownedListener(plugin, downedManager), plugin);
        pm.registerEvents(new SaqueadorListener(plugin, saqueadorManager), plugin);
        pm.registerEvents(new ClassRespawnListener(plugin, this), plugin);

        // Restaura o estado "Derrubado" ao logar (sobrevive ao relog).
        plugin.getProfileManager().onProfileLoaded((player, profile) -> downedManager.restore(player, profile));

        tickService.registerSecondHandler(this::diagnosisTick);

        plugin.getItemRegistry().register(new RadioFrequencia(plugin));
        plugin.getItemRegistry().register(new PeDeCabra(plugin));
    }

    public void reload() {
        config.load();
        skills.load();
    }

    public ClassConfig config() {
        return config;
    }

    public SkillRegistry skills() {
        return skills;
    }

    public DownedManager downed() {
        return downedManager;
    }

    public SaqueadorManager saqueador() {
        return saqueadorManager;
    }

    public boolean canSelect(PlayerProfile profile) {
        return config.allowChange() || profile.getPlayerClass() == PlayerClass.NONE;
    }

    public void selectClass(Player player, PlayerProfile profile, PlayerClass clazz) {
        profile.setPlayerClass(clazz);
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        player.sendMessage(Component.text("Classe escolhida: ", NamedTextColor.GRAY)
                .append(Component.text(clazz.name(), NamedTextColor.GOLD)));
    }

    public void grantXp(Player player, PlayerProfile profile, long amount, String reason) {
        if (amount <= 0) {
            return;
        }
        profile.addXp(amount);
        player.sendActionBar(Component.text("+" + amount + " XP (" + reason + ") — total: " + profile.getXp(),
                NamedTextColor.GREEN));
        player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.5f);
    }

    public UnlockResult tryUnlock(PlayerProfile profile, SkillNode node) {
        if (profile.hasSkill(node.id())) {
            return UnlockResult.ALREADY;
        }
        if (node.requiredClass() != PlayerClass.NONE && profile.getPlayerClass() != node.requiredClass()) {
            return UnlockResult.WRONG_CLASS;
        }
        for (String prereq : node.prerequisites()) {
            if (!profile.hasSkill(prereq)) {
                return UnlockResult.LOCKED;
            }
        }
        if (profile.getXp() < node.cost()) {
            return UnlockResult.NO_XP;
        }
        profile.setXp(profile.getXp() - node.cost());
        profile.unlockSkill(node.id());
        return UnlockResult.SUCCESS;
    }

    private void diagnosisTick(PlayerProfile profile) {
        if (profile.getPlayerClass() != PlayerClass.MEDICO || !profile.hasSkill(SKILL_DIAGNOSIS)) {
            return;
        }
        Player medic = plugin.getServer().getPlayer(profile.getUuid());
        if (medic == null || !medic.isSneaking()) {
            return;
        }
        Entity target = medic.getTargetEntity(config.diagnosisRange());
        if (!(target instanceof Player ally) || ally.equals(medic)) {
            return;
        }
        PlayerProfile allyProfile = plugin.getProfileManager().get(ally.getUniqueId());
        if (allyProfile == null) {
            return;
        }
        medic.sendActionBar(Component.text(
                ally.getName()
                        + "  ❤ " + (int) ally.getHealth() + "/" + (int) maxHealth(ally)
                        + "  ☣ " + (int) allyProfile.getInfectionLevel() + "%"
                        + "  🧠 " + (int) allyProfile.getSanity(),
                NamedTextColor.AQUA));
    }

    @SuppressWarnings("deprecation")
    private double maxHealth(Player player) {
        return player.getMaxHealth();
    }
}
