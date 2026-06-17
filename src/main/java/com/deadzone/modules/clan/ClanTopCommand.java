package com.deadzone.modules.clan;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** /clantop — ranking dos melhores clãs (tier, integrantes, cofre). */
public class ClanTopCommand implements CommandExecutor {

    private final DeadzonePlugin plugin;

    public ClanTopCommand(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        List<Clan> top = plugin.getClanManager().topClans(10);
        sender.sendMessage(Component.text("★ Top Clãs", NamedTextColor.GOLD));
        if (top.isEmpty()) {
            sender.sendMessage(Component.text("Nenhum clã fundado ainda.", NamedTextColor.GRAY));
            return true;
        }
        int rank = 1;
        for (Clan clan : top) {
            NamedTextColor c = rank == 1 ? NamedTextColor.YELLOW
                    : rank == 2 ? NamedTextColor.WHITE
                    : rank == 3 ? NamedTextColor.GOLD : NamedTextColor.GRAY;
            sender.sendMessage(Component.text(rank + ". ", c)
                    .append(Component.text("[" + clan.tag() + "] " + clan.name(), clan.namedColor()))
                    .append(Component.text("  •  Tier " + clan.level() + "  •  " + clan.size() + " membros"
                            + "  •  " + plugin.getEconomyManager().format(clan.bank()), NamedTextColor.GRAY)));
            rank++;
        }
        return true;
    }
}
