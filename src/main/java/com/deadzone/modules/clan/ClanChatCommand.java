package com.deadzone.modules.clan;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /c <mensagem> — atalho para o chat do clan. */
public class ClanChatCommand implements CommandExecutor {

    private final DeadzonePlugin plugin;

    public ClanChatCommand(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Uso: /c <mensagem>", NamedTextColor.GRAY));
            return true;
        }
        plugin.getClanManager().chat(player, String.join(" ", args));
        return true;
    }
}
