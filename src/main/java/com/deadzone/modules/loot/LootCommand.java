package com.deadzone.modules.loot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Admin: marca/desmarca baús de loot e recarrega as tabelas. */
public class LootCommand implements CommandExecutor, TabCompleter {

    private static final Set<Material> MARKABLE = EnumSet.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL);

    private final LootManager manager;

    public LootCommand(LootManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("deadzone.admin.loot")) {
            sender.sendMessage(Component.text("Sem permissão.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text("Uso: /loot <mark|unmark|list|reload> [tipo]", NamedTextColor.GRAY));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "mark" -> mark(sender, args);
            case "unmark" -> unmark(sender);
            case "list" -> sender.sendMessage(Component.text("Baús de loot registrados: " + manager.count(),
                    NamedTextColor.AQUA));
            case "reload" -> {
                manager.reload();
                sender.sendMessage(Component.text("loot-tables.yml recarregado.", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Subcomando inválido.", NamedTextColor.RED));
        }
        return true;
    }

    private void mark(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Apenas jogadores (mire num baú).", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Uso: /loot mark <" + String.join("|", manager.types()) + ">",
                    NamedTextColor.GRAY));
            return;
        }
        String type = args[1].toLowerCase(Locale.ROOT);
        if (!manager.hasType(type)) {
            player.sendMessage(Component.text("Tipo inválido. Tipos: " + String.join(", ", manager.types()),
                    NamedTextColor.RED));
            return;
        }
        Block block = targetContainer(player);
        if (block == null) {
            player.sendMessage(Component.text("Mire num baú ou barril (até 5 blocos).", NamedTextColor.RED));
            return;
        }
        manager.mark(block, type);
        player.sendMessage(Component.text("Baú de loot marcado como " + type + ".", NamedTextColor.GREEN));
    }

    private void unmark(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Apenas jogadores (mire num baú).", NamedTextColor.RED));
            return;
        }
        Block block = player.getTargetBlockExact(5);
        if (block == null || manager.getContainer(block) == null) {
            player.sendMessage(Component.text("Mire num baú de loot marcado.", NamedTextColor.RED));
            return;
        }
        manager.unmark(block);
        player.sendMessage(Component.text("Baú de loot desmarcado.", NamedTextColor.YELLOW));
    }

    private Block targetContainer(Player player) {
        Block block = player.getTargetBlockExact(5);
        return (block != null && MARKABLE.contains(block.getType())) ? block : null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("mark", "unmark", "list", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mark")) {
            return filter(new ArrayList<>(manager.types()), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
