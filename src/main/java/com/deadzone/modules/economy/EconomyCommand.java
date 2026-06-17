package com.deadzone.modules.economy;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Um executor para todos os comandos de economia (saldo, pix/pay/pagar, cobrar, baltop, eco). */
public class EconomyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ECO_SUB = List.of("dar", "tirar", "definir", "resetar");
    private static final List<String> COBRAR_SUB = List.of("pagar", "confirmar", "recusar");

    private final DeadzonePlugin plugin;

    public EconomyCommand(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    private EconomyManager eco() {
        return plugin.getEconomyManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "saldo" -> saldo(sender);
            case "pix", "pay", "pagar" -> pay(sender, args);
            case "cobrar" -> cobrar(sender, args);
            case "baltop" -> baltop(sender);
            case "eco" -> eco(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void saldo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Apenas jogadores têm saldo.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Seu saldo: " + eco().format(eco().balanceOf(player)),
                NamedTextColor.GOLD));
    }

    private void pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Apenas jogadores podem transferir.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Uso: /pagar <jogador> <valor>", NamedTextColor.GRAY));
            return;
        }
        Long amount = parseAmount(player, args[1]);
        if (amount == null) {
            return;
        }
        eco().pay(player, args[0], amount);
    }

    private void cobrar(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Apenas jogadores podem cobrar.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Uso: /cobrar <jogador> <valor>", NamedTextColor.GRAY));
            return;
        }
        // Subcomandos disparados pelos botões clicáveis: /cobrar <pagar|confirmar|recusar> <id>
        String first = args[0].toLowerCase(Locale.ROOT);
        if (COBRAR_SUB.contains(first)) {
            Integer id = parseInt(args[1]);
            if (id == null) {
                return;
            }
            switch (first) {
                case "pagar" -> eco().chargePayClicked(player, id);
                case "confirmar" -> eco().chargeConfirmClicked(player, id);
                case "recusar" -> eco().chargeDeclineClicked(player, id);
                default -> { }
            }
            return;
        }
        Long amount = parseAmount(player, args[1]);
        if (amount == null) {
            return;
        }
        eco().createCharge(player, args[0], amount);
    }

    private void baltop(CommandSender sender) {
        List<EconomyDao.TopEntry> top = eco().top();
        sender.sendMessage(Component.text("★ Top 10 mais ricos", NamedTextColor.GOLD));
        if (top.isEmpty()) {
            sender.sendMessage(Component.text("Ninguém tem scraps ainda.", NamedTextColor.GRAY));
            return;
        }
        int rank = 1;
        for (EconomyDao.TopEntry e : top) {
            NamedTextColor color = rank == 1 ? NamedTextColor.YELLOW
                    : rank == 2 ? NamedTextColor.WHITE
                    : rank == 3 ? NamedTextColor.GOLD : NamedTextColor.GRAY;
            sender.sendMessage(Component.text(rank + ". " + e.name() + " — " + eco().format(e.balance()), color));
            rank++;
        }
    }

    private void eco(CommandSender sender, String[] args) {
        if (!sender.hasPermission("deadzone.admin.eco")) {
            sender.sendMessage(Component.text("Sem permissão.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /eco <dar|tirar|definir|resetar> <jogador> [valor]",
                    NamedTextColor.GRAY));
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String name = args[1];
        if (sub.equals("resetar")) {
            eco().adminReset(sender, name);
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Uso: /eco " + sub + " <jogador> <valor>", NamedTextColor.GRAY));
            return;
        }
        Long value = parseAmount(sender, args[2]);
        if (value == null) {
            return;
        }
        switch (sub) {
            case "dar" -> eco().adminGive(sender, name, value);
            case "tirar" -> eco().adminTake(sender, name, value);
            case "definir" -> eco().adminSet(sender, name, value);
            default -> sender.sendMessage(Component.text("Subcomando inválido. Use dar/tirar/definir/resetar.",
                    NamedTextColor.RED));
        }
    }

    private Long parseAmount(CommandSender sender, String raw) {
        try {
            long v = Long.parseLong(raw);
            if (v <= 0) {
                sender.sendMessage(Component.text("O valor precisa ser positivo.", NamedTextColor.RED));
                return null;
            }
            return v;
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Valor inválido: " + raw, NamedTextColor.RED));
            return null;
        }
    }

    private Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("eco")) {
            if (args.length == 1) {
                return filter(ECO_SUB, args[0]);
            }
            if (args.length == 2) {
                return onlinePlayers(args[1]);
            }
            return List.of();
        }
        if ((name.equals("pix") || name.equals("pay") || name.equals("pagar") || name.equals("cobrar"))
                && args.length == 1) {
            return onlinePlayers(args[0]);
        }
        return List.of();
    }

    private List<String> onlinePlayers(String prefix) {
        List<String> out = new ArrayList<>();
        String p = prefix.toLowerCase(Locale.ROOT);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(online.getName());
            }
        }
        return out;
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
