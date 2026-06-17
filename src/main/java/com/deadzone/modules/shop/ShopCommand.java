package com.deadzone.modules.shop;

import com.deadzone.DeadzonePlugin;
import com.deadzone.modules.shop.gui.ArmeiroMenu;
import com.deadzone.modules.shop.gui.ShopMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** Um executor para /medico, /armeiro e /comprador. */
public class ShopCommand implements CommandExecutor {

    private final DeadzonePlugin plugin;

    public ShopCommand(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "medico" -> new ShopMenu(plugin, "medico").open(player);
            case "armeiro" -> new ArmeiroMenu(plugin).open(player);
            case "comprador" -> new ShopMenu(plugin, "comprador").open(player);
            default -> {
                return false;
            }
        }
        return true;
    }
}
