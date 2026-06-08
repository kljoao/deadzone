package com.deadzone.modules.sanity.command;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * /base set|remove|info — define o "lar" do jogador (recuperação de sanidade).
 */
public class BaseCommand implements CommandExecutor, TabCompleter {

    private final DeadzonePlugin plugin;

    public BaseCommand(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return true;
        }
        String action = args.length > 0 ? args[0].toLowerCase() : "set";
        switch (action) {
            case "set" -> {
                plugin.getSanityManager().bases().setBase(player);
                player.sendMessage(Component.text("Base definida na sua posição atual.", NamedTextColor.GREEN));
            }
            case "remove" -> {
                plugin.getSanityManager().bases().removeBase(player.getUniqueId());
                player.sendMessage(Component.text("Base removida.", NamedTextColor.YELLOW));
            }
            case "info" -> {
                var base = plugin.getSanityManager().bases().getBase(player.getUniqueId());
                if (base == null) {
                    player.sendMessage(Component.text("Você não tem base definida. Use /base set.", NamedTextColor.GRAY));
                } else {
                    player.sendMessage(Component.text("Sua base: " + base.getWorld().getName()
                            + " " + (int) base.getX() + ", " + (int) base.getY() + ", " + (int) base.getZ(),
                            NamedTextColor.GRAY));
                }
            }
            default -> player.sendMessage(Component.text("Uso: /base <set|remove|info>", NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 ? List.of("set", "remove", "info") : List.of();
    }
}
