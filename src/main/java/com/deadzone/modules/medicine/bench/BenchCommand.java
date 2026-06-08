package com.deadzone.modules.medicine.bench;

import com.deadzone.DeadzonePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /bancada — abre a Bancada Médica. */
public class BenchCommand implements CommandExecutor {

    private final DeadzonePlugin plugin;

    public BenchCommand(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return true;
        }
        new BenchMenu(plugin).open(player);
        return true;
    }
}
