package com.deadzone.modules.classes.command;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.PlayerClass;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.modules.classes.gui.SkillTreeMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /skills — abre a árvore de habilidades da classe do jogador.
 */
public class SkillsCommand implements CommandExecutor {

    private final DeadzonePlugin plugin;

    public SkillsCommand(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return true;
        }
        PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
        if (profile == null || profile.getPlayerClass() == PlayerClass.NONE) {
            player.sendMessage(Component.text("Escolha uma classe primeiro com /classe.", NamedTextColor.RED));
            return true;
        }
        new SkillTreeMenu(plugin).open(player);
        return true;
    }
}
