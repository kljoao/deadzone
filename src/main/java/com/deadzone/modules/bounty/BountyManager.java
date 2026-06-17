package com.deadzone.modules.bounty;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounty (recompensa pela cabeça). Vive no perfil (persistido). Notoriedade automática por abate
 * (com anti-farm por vítima) + bounty colocado por outros jogadores com scraps próprios. Quem mata
 * um alvo com bounty recebe o valor; o bounty do alvo zera ao ser reivindicado.
 */
public class BountyManager {

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;

    private long notorietyPerKill = 100;
    private long sameVictimCooldownMs = 600_000L;
    private long minPlace = 50;

    // killer -> (vítima -> último abate ms): anti-farm da notoriedade
    private final Map<UUID, Map<UUID, Long>> lastNotoriety = new ConcurrentHashMap<>();
    private volatile List<TopEntry> topCache = List.of();

    public BountyManager(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    public void load() {
        var c = configManager.loadConfig("bounty.yml");
        this.notorietyPerKill = Math.max(0, c.getLong("notoriety-per-kill", 100));
        this.sameVictimCooldownMs = Math.max(0, c.getLong("same-victim-cooldown-seconds", 600)) * 1000L;
        this.minPlace = Math.max(1, c.getLong("min-place", 50));
    }

    public void enable() {
        refreshTop();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshTop, 200L, 1200L); // a cada 60s
    }

    public void reload() {
        load();
    }

    public long minPlace() {
        return minPlace;
    }

    private PlayerProfile profileOf(Player p) {
        return p == null ? null : plugin.getProfileManager().get(p.getUniqueId());
    }

    public long bountyOf(Player p) {
        PlayerProfile prof = profileOf(p);
        return prof != null ? prof.getBounty() : 0L;
    }

    // ----- colocar bounty (alvo precisa estar online) -----

    public void place(Player placer, String targetName, long amount) {
        if (amount < minPlace) {
            error(placer, "O bounty mínimo é " + plugin.getEconomyManager().format(minPlace) + ".");
            return;
        }
        if (placer.getName().equalsIgnoreCase(targetName)) {
            error(placer, "Você não pode colocar bounty em si mesmo.");
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            error(placer, "Esse jogador precisa estar online.");
            return;
        }
        PlayerProfile targetProfile = profileOf(target);
        if (targetProfile == null) {
            error(placer, "Perfil do alvo ainda carregando.");
            return;
        }
        if (!plugin.getEconomyManager().tryDebit(placer, amount)) {
            error(placer, "Saldo insuficiente — você tem " + plugin.getEconomyManager().format(
                    plugin.getEconomyManager().balanceOf(placer)) + ".");
            return;
        }
        targetProfile.addBounty(amount);
        long total = targetProfile.getBounty();
        Bukkit.getServer().broadcast(Component.text("☠ ", NamedTextColor.RED)
                .append(Component.text(placer.getName() + " colocou " + plugin.getEconomyManager().format(amount)
                        + " na cabeça de " + target.getName() + "! Recompensa total: "
                        + plugin.getEconomyManager().format(total) + ".", NamedTextColor.GOLD)));
        target.playSound(target, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
    }

    // ----- abate (chamado pelo listener) -----

    public void onPvpKill(Player killer, Player victim) {
        PlayerProfile victimProfile = profileOf(victim);
        long reward = victimProfile != null ? victimProfile.getBounty() : 0L;
        if (reward > 0 && victimProfile != null) {
            victimProfile.setBounty(0L); // reivindicado
            plugin.getEconomyManager().reward(killer, reward);
            Bukkit.getServer().broadcast(Component.text("💀 ", NamedTextColor.RED)
                    .append(Component.text(killer.getName() + " reivindicou o bounty de " + victim.getName()
                            + " e levou " + plugin.getEconomyManager().format(reward) + "!", NamedTextColor.GOLD)));
            killer.playSound(killer, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        }
        // notoriedade: o matador fica mais procurado (com anti-farm por vítima)
        if (notorietyPerKill > 0 && !killedRecently(killer.getUniqueId(), victim.getUniqueId())) {
            PlayerProfile killerProfile = profileOf(killer);
            if (killerProfile != null) {
                killerProfile.addBounty(notorietyPerKill);
                killer.sendActionBar(Component.text("Sua cabeça agora vale "
                        + plugin.getEconomyManager().format(killerProfile.getBounty()) + ".", NamedTextColor.RED));
            }
            markKilled(killer.getUniqueId(), victim.getUniqueId());
        }
    }

    private boolean killedRecently(UUID killer, UUID victim) {
        Map<UUID, Long> victims = lastNotoriety.get(killer);
        if (victims == null) {
            return false;
        }
        Long last = victims.get(victim);
        return last != null && System.currentTimeMillis() - last < sameVictimCooldownMs;
    }

    private void markKilled(UUID killer, UUID victim) {
        lastNotoriety.computeIfAbsent(killer, k -> new ConcurrentHashMap<>()).put(victim, System.currentTimeMillis());
    }

    public void clearCache(UUID uuid) {
        lastNotoriety.remove(uuid);
    }

    // ----- ranking (top procurados) -----

    public List<TopEntry> top() {
        return topCache;
    }

    private void refreshTop() {
        plugin.getProfileManager().submitDbTask(() -> {
            List<TopEntry> fresh = new ArrayList<>();
            try (Connection c = plugin.getDatabase().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT name, bounty FROM players WHERE bounty > 0 ORDER BY bounty DESC LIMIT 10");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fresh.add(new TopEntry(rs.getString("name"), rs.getLong("bounty")));
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Bounty (top) falhou: " + e.getMessage());
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> this.topCache = fresh);
        });
    }

    private void error(CommandSender sender, String msg) {
        sender.sendMessage(Component.text(msg, NamedTextColor.RED));
    }

    public record TopEntry(String name, long bounty) {
    }
}
