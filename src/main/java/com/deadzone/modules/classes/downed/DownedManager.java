package com.deadzone.modules.classes.downed;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.DownedState;
import com.deadzone.core.profile.PlayerClass;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.modules.classes.ClassConfig;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Estado "Derrubado": em vez de morrer, o jogador cai imóvel e invulnerável esperando um Médico revivê-lo. */
public class DownedManager {

    private static final String SKILL_REANIMACAO = "med_reanimacao";

    private final DeadzonePlugin plugin;
    private final ClassConfig config;
    private final Map<UUID, Downed> downed = new HashMap<>();

    public DownedManager(DeadzonePlugin plugin, ClassConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean isDowned(UUID uuid) {
        return downed.containsKey(uuid);
    }

    /** Jogador derrubado na mira do reanimador (até 5 blocos), ou null. */
    public Player getTargetDowned(Player reviver) {
        Entity entity = reviver.getTargetEntity(5);
        if (entity instanceof Player target && isDowned(target.getUniqueId())) {
            return target;
        }
        return null;
    }

    public boolean enabled() {
        return config.downedEnabled();
    }

    /** Coloca o jogador no estado derrubado. */
    public void enterDowned(Player player, PlayerProfile profile) {
        if (downed.containsKey(player.getUniqueId())) {
            return;
        }
        int seconds = config.downedDurationSeconds();
        long now = System.currentTimeMillis();
        profile.setDownedState(new DownedState(now, now + seconds * 1000L));
        player.playSound(player, Sound.ENTITY_PLAYER_BIG_FALL, 1f, 0.5f);
        announceNearby(player);
        apply(player, seconds * 20);
    }

    /** Re-aplica o estado ao logar, com o tempo restante (ou mata se já expirou offline). */
    public void restore(Player player, PlayerProfile profile) {
        DownedState ds = profile.getDownedState();
        if (ds == null || downed.containsKey(player.getUniqueId())) {
            return;
        }
        long remainingMs = ds.getExpiresAt() - System.currentTimeMillis();
        if (remainingMs <= 0) {
            profile.setDownedState(null);
            realDeath(player); // o tempo esgotou enquanto estava offline
            return;
        }
        apply(player, (int) (remainingMs / 50L));
        player.sendActionBar(Component.text("Você ainda está derrubado — peça ajuda!", NamedTextColor.RED));
    }

    /** Aplica visual/efeitos/bossbar/timer do estado derrubado por N ticks. */
    private void apply(Player player, int totalTicks) {
        player.setHealth(1.0); // à beira da morte (e invulnerável)
        float originalWalkSpeed = player.getWalkSpeed();
        player.setWalkSpeed(0f);
        player.setSprinting(false);
        addEffect(player, "slowness", 255, totalTicks + 40);
        addEffect(player, "jump_boost", 128, totalTicks + 40);
        addEffect(player, "blindness", 0, totalTicks + 40);

        BossBar bar = BossBar.bossBar(
                Component.text("DERRUBADO — peça ajuda!", NamedTextColor.RED),
                1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);

        Downed state = new Downed(originalWalkSpeed, bar, totalTicks);
        state.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(player, state), 1L, 1L);
        downed.put(player.getUniqueId(), state);
    }

    /** Desanexa o runtime ao sair, mas PRESERVA o estado no profile (persiste downed_until). */
    public void detach(Player player) {
        Downed state = downed.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (state.task != null) {
            state.task.cancel();
        }
        player.hideBossBar(state.bar);
        // walkspeed/efeitos somem no logout; profile.downedState fica salvo
    }

    private void tick(Player player, Downed state) {
        state.elapsed++;
        float remaining = 1f - Math.min(1f, (float) state.elapsed / state.totalTicks);
        state.bar.progress(remaining);
        if (state.elapsed % 20 == 0) {
            player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 0.3, 0),
                    10, 0.3, 0.1, 0.3, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(120, 0, 0), 1.4f));
        }
        if (state.elapsed >= state.totalTicks) {
            realDeath(player);
        }
    }

    /** Tenta reviver um jogador derrubado. Requer Médico com a skill de Reanimação. */
    public boolean tryRevive(Player reviver, Player target) {
        if (!isDowned(target.getUniqueId())) {
            return false;
        }
        PlayerProfile rev = plugin.getProfileManager().get(reviver.getUniqueId());
        if (rev == null || rev.getPlayerClass() != PlayerClass.MEDICO || !rev.hasSkill(SKILL_REANIMACAO)) {
            reviver.sendActionBar(Component.text("Você não sabe reanimar (Médico).", NamedTextColor.RED));
            return false;
        }
        cleanup(target);

        double maxHealth = maxHealth(target);
        target.setHealth(Math.max(1.0, maxHealth * config.reviveHealthPercent() / 100.0));
        target.playSound(target, Sound.ITEM_TOTEM_USE, 1f, 1f);
        target.sendTitle("", "");
        target.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("Reanimado!", NamedTextColor.GREEN),
                Component.text("Você foi salvo por " + reviver.getName(), NamedTextColor.GRAY)));
        reviver.sendActionBar(Component.text("Você reanimou " + target.getName() + "!", NamedTextColor.GREEN));

        PlayerProfile profile = plugin.getProfileManager().get(reviver.getUniqueId());
        if (profile != null) {
            plugin.getClassManager().grantXp(reviver, profile, config.xpReviveAlly(), "reanimação");
        }
        return true;
    }

    private void realDeath(Player player) {
        cleanup(player);
        // não é morte por infecção; segue para morte real -> wipe
        player.setHealth(0.0);
    }

    /** Remove o estado derrubado e restaura o jogador (sem matar). */
    public void cleanup(Player player) {
        Downed state = downed.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (state.task != null) {
            state.task.cancel();
        }
        player.setWalkSpeed(state.originalWalkSpeed);
        removeEffect(player, "slowness");
        removeEffect(player, "jump_boost");
        removeEffect(player, "blindness");
        player.hideBossBar(state.bar);
        PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
        if (profile != null) {
            profile.setDownedState(null);
        }
    }

    private void announceNearby(Player player) {
        player.getWorld().getNearbyPlayers(player.getLocation(), 20).forEach(other -> {
            if (!other.equals(player)) {
                other.sendActionBar(Component.text(player.getName() + " foi derrubado! Reanime-o!", NamedTextColor.RED));
            }
        });
    }

    @SuppressWarnings("deprecation")
    private double maxHealth(Player player) {
        return player.getMaxHealth();
    }

    private void addEffect(Player player, String id, int amplifier, int ticks) {
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(id));
        if (type != null) {
            player.addPotionEffect(new PotionEffect(type, ticks, amplifier, true, false, false));
        }
    }

    private void removeEffect(Player player, String id) {
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(id));
        if (type != null) {
            player.removePotionEffect(type);
        }
    }

    private static final class Downed {
        final float originalWalkSpeed;
        final BossBar bar;
        final int totalTicks;
        int elapsed;
        BukkitTask task;

        Downed(float originalWalkSpeed, BossBar bar, int totalTicks) {
            this.originalWalkSpeed = originalWalkSpeed;
            this.bar = bar;
            this.totalTicks = totalTicks;
        }
    }
}
