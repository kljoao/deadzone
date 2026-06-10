package com.deadzone.modules.claim;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /confirmar base — confirma a posição travada e cria a base. */
public class ConfirmarBaseCommand implements CommandExecutor {

    private final DeadzonePlugin plugin;

    public ConfirmarBaseCommand(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores.");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("base")) {
            player.sendMessage(Component.text("Use: /confirmar base", NamedTextColor.GRAY));
            return true;
        }
        plugin.getClaimManager().confirmPending(player);
        return true;
    }
}
