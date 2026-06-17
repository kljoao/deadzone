package com.deadzone.modules.bounty;

import com.deadzone.DeadzonePlugin;
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

/** /bounty [top | <jogador> [valor]] — ver/colocar recompensa pela cabeça. */
public class BountyCommand implements CommandExecutor, TabCompleter {

    private final DeadzonePlugin plugin;

    public BountyCommand(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    private BountyManager bounty() {
        return plugin.getBountyManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("top")) {
            showTop(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Use /bounty top.", NamedTextColor.GRAY));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Sua cabeça vale: "
                    + plugin.getEconomyManager().format(bounty().bountyOf(player)), NamedTextColor.GOLD));
            player.sendMessage(Component.text("Uso: /bounty <jogador> <valor>  •  /bounty top", NamedTextColor.GRAY));
            return true;
        }
        if (args.length == 1) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(Component.text("Esse jogador precisa estar online.", NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text("Cabeça de " + target.getName() + " vale: "
                        + plugin.getEconomyManager().format(bounty().bountyOf(target)), NamedTextColor.GOLD));
            }
            return true;
        }
        // /bounty <jogador> <valor>
        Long amount = parseAmount(player, args[1]);
        if (amount != null) {
            bounty().place(player, args[0], amount);
        }
        return true;
    }

    private void showTop(CommandSender sender) {
        List<BountyManager.TopEntry> top = bounty().top();
        sender.sendMessage(Component.text("☠ Mais Procurados", NamedTextColor.RED));
        if (top.isEmpty()) {
            sender.sendMessage(Component.text("Ninguém tem bounty no momento.", NamedTextColor.GRAY));
            return;
        }
        int rank = 1;
        for (BountyManager.TopEntry e : top) {
            sender.sendMessage(Component.text(rank++ + ". " + e.name() + " — "
                    + plugin.getEconomyManager().format(e.bounty()), NamedTextColor.GOLD));
        }
    }

    private Long parseAmount(CommandSender sender, String raw) {
        try {
            long v = Long.parseLong(raw);
            if (v <= 0) {
                sender.sendMessage(Component.text("O valor precisa ser positivo.", NamedTextColor.RED));
                return null;
            }
            return v;
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Valor inválido: " + raw, NamedTextColor.RED));
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            out.add("top");
            String p = args[0].toLowerCase(Locale.ROOT);
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(p)) {
                    out.add(online.getName());
                }
            }
            return out;
        }
        return List.of();
    }
}
