package com.deadzone.modules.daily;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /diario — abre o menu de recompensa diária (streak de login). */
public class DailyCommand implements CommandExecutor {

    private final DeadzonePlugin plugin;

    public DailyCommand(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")
                && sender.hasPermission("deadzone.admin.reload")) {
            plugin.getDailyRewardManager().reload();
            sender.sendMessage(Component.text("daily-rewards.yml recarregado.", NamedTextColor.GREEN));
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return true;
        }
        new DailyMenu(plugin).open(player);
        return true;
    }
}
