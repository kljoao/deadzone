package com.deadzone.modules.classes.command;

import com.deadzone.DeadzonePlugin;
import com.deadzone.modules.classes.gui.ClassMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /classe — abre o menu de seleção de classe.
 */
public class ClassCommand implements CommandExecutor {

    private final DeadzonePlugin plugin;

    public ClassCommand(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return true;
        }
        new ClassMenu(plugin).open(player);
        return true;
    }
}
