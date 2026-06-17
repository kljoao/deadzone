package com.deadzone.modules.stats;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /stats [jogador] — abre o menu de estatísticas (de si ou de outro jogador online). */
public class StatsCommand implements CommandExecutor, TabCompleter {

    private final DeadzonePlugin plugin;

    public StatsCommand(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Component.text("Apenas jogadores podem ver estatísticas.", NamedTextColor.RED));
            return true;
        }
        Player target = args.length >= 1 ? Bukkit.getPlayerExact(args[0]) : viewer;
        if (target == null) {
            viewer.sendMessage(Component.text("Esse jogador precisa estar online.", NamedTextColor.RED));
            return true;
        }
        PlayerProfile profile = plugin.getProfileManager().get(target.getUniqueId());
        if (profile == null) {
            viewer.sendMessage(Component.text("Perfil ainda carregando, tente de novo.", NamedTextColor.GRAY));
            return true;
        }
        new StatsMenu(plugin, target.getUniqueId(), target.getName(), profile).open(viewer);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String p = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(online.getName());
            }
        }
        return out;
    }
}
