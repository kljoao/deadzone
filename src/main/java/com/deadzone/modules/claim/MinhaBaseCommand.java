package com.deadzone.modules.claim;

import com.deadzone.DeadzonePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /minhabase — mostra as extremidades da sua base por alguns segundos (só pra você). */
public class MinhaBaseCommand implements CommandExecutor {

    private final DeadzonePlugin plugin;

    public MinhaBaseCommand(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores.");
            return true;
        }
        plugin.getClaimManager().showLimits(player);
        return true;
    }
}
