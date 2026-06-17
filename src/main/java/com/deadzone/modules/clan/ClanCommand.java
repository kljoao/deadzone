package com.deadzone.modules.clan;

import com.deadzone.DeadzonePlugin;
import com.deadzone.modules.clan.gui.ClanMenu;
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
import java.util.UUID;

/** /clan — gestão completa de clans. */
public class ClanCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = List.of(
            "criar", "convidar", "aceitar", "recusar", "sair", "expulsar", "promover", "rebaixar",
            "transferir", "info", "lista", "dissolver", "evoluir", "cor", "ff", "glow", "simbolo", "banco", "chat", "ajuda");
    private static final List<String> TOGGLE = List.of("on", "off");
    private static final List<String> BANK_SUB = List.of("depositar", "sacar");

    private final DeadzonePlugin plugin;

    public ClanCommand(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    private ClanManager clans() {
        return plugin.getClanManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")
                && sender.hasPermission("deadzone.admin.reload")) {
            clans().reload();
            sender.sendMessage(Component.text("clans.yml recarregado.", NamedTextColor.GREEN));
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            new ClanMenu(plugin).open(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu", "gui" -> new ClanMenu(plugin).open(player);
            case "criar", "create" -> {
                if (args.length < 3) {
                    usage(player, "/clan criar <nome> <tag>");
                } else {
                    clans().create(player, args[1], args[2]);
                }
            }
            case "convidar", "invite" -> needArg(player, args, "/clan convidar <jogador>",
                    () -> clans().invite(player, args[1]));
            case "aceitar", "accept" -> clans().accept(player);
            case "recusar", "deny" -> clans().deny(player);
            case "sair", "leave" -> clans().leave(player);
            case "expulsar", "kick" -> needArg(player, args, "/clan expulsar <jogador>",
                    () -> clans().kick(player, args[1]));
            case "promover", "promote" -> needArg(player, args, "/clan promover <jogador>",
                    () -> clans().promote(player, args[1]));
            case "rebaixar", "demote" -> needArg(player, args, "/clan rebaixar <jogador>",
                    () -> clans().demote(player, args[1]));
            case "transferir", "transfer" -> needArg(player, args, "/clan transferir <jogador>",
                    () -> clans().transfer(player, args[1]));
            case "info" -> info(player, args.length >= 2 ? args[1] : null);
            case "lista", "list" -> list(player);
            case "dissolver", "disband" -> clans().disband(player);
            case "evoluir", "upgrade" -> clans().upgradeTier(player);
            case "cor", "color" -> needArg(player, args, "/clan cor <cor>",
                    () -> clans().setColor(player, args[1]));
            case "ff", "friendlyfire" -> toggle(player, args, b -> clans().setFriendlyFire(player, b),
                    "/clan ff <on|off>");
            case "glow" -> toggle(player, args, b -> clans().setGlow(player, b), "/clan glow <on|off>");
            case "simbolo", "symbol" -> toggle(player, args, b -> clans().setSymbol(player, b),
                    "/clan simbolo <on|off>");
            case "banco", "bank" -> bank(player, args);
            case "chat", "c" -> {
                if (args.length < 2) {
                    usage(player, "/clan chat <mensagem>");
                } else {
                    clans().chat(player, join(args, 1));
                }
            }
            default -> help(player);
        }
        return true;
    }

    private void needArg(Player player, String[] args, String usage, Runnable action) {
        if (args.length < 2) {
            usage(player, usage);
        } else {
            action.run();
        }
    }

    private void toggle(Player player, String[] args, java.util.function.Consumer<Boolean> action, String usage) {
        if (args.length < 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
            usage(player, usage);
            return;
        }
        action.accept(args[1].equalsIgnoreCase("on"));
    }

    private void bank(Player player, String[] args) {
        if (args.length < 3) {
            usage(player, "/clan banco <depositar|sacar> <valor>");
            return;
        }
        Long amount = parseAmount(player, args[2]);
        if (amount == null) {
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "depositar", "deposit" -> clans().bankDeposit(player, amount);
            case "sacar", "withdraw" -> clans().bankWithdraw(player, amount);
            default -> usage(player, "/clan banco <depositar|sacar> <valor>");
        }
    }

    private void info(Player player, String name) {
        Clan clan = name != null ? clans().getByName(name) : clans().getClanOf(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(Component.text(name != null ? "Clan não encontrado."
                    : "Você não está em um clan. Use /clan criar <nome> <tag>.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(clans().prefix(clan).append(Component.text(clan.name(), clan.namedColor())));
        String tierLine = "Tier " + clan.level();
        if (clans().isMaxTier(clan)) {
            tierLine += " (máx)";
        } else {
            tierLine += " → próx: " + clans().nextTierMembers(clan) + " vagas por "
                    + plugin.getEconomyManager().format(clans().nextTierCost(clan));
        }
        player.sendMessage(Component.text("Integrantes: " + clan.size() + "/" + clans().maxMembersFor(clan)
                + "  •  " + tierLine + "  •  Cofre: " + plugin.getEconomyManager().format(clan.bank()),
                NamedTextColor.GRAY));
        UUID self = player.getUniqueId();
        if (clan.isMember(self)) {
            player.sendMessage(Component.text("Seu cargo: " + clan.roleOf(self).displayName(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("FF: " + onOff(clan.friendlyFire()) + "  •  Glow: "
                    + onOff(clan.glow()) + "  •  Símbolo: " + onOff(clan.symbol()), NamedTextColor.DARK_GRAY));
        }
        // lista os integrantes por cargo (do maior para o menor)
        List<ClanMember> sorted = new ArrayList<>(clan.members());
        sorted.sort((a, b) -> Integer.compare(b.role().weight(), a.role().weight()));
        StringBuilder sb = new StringBuilder();
        for (ClanMember m : sorted) {
            Player online = plugin.getServer().getPlayer(m.uuid());
            String dot = online != null ? "<green>● " : "<gray>● ";
            sb.append(dot).append(m.role().colorTag()).append(m.lastKnownName())
                    .append(" <dark_gray>(").append(m.role().displayName()).append(")<reset>  ");
        }
        player.sendMessage(plugin.getMessages().parse(sb.toString().trim()));
    }

    private void list(Player player) {
        player.sendMessage(Component.text("★ Clans (" + clans().all().size() + ")", NamedTextColor.GOLD));
        if (clans().all().isEmpty()) {
            player.sendMessage(Component.text("Nenhum clan fundado ainda.", NamedTextColor.GRAY));
            return;
        }
        for (Clan clan : clans().all()) {
            player.sendMessage(clans().prefix(clan)
                    .append(Component.text(clan.name(), clan.namedColor()))
                    .append(Component.text(" — " + clan.size() + " integrante(s)", NamedTextColor.GRAY)));
        }
    }

    private void help(Player player) {
        player.sendMessage(Component.text("=== Clãs ===", NamedTextColor.GOLD));
        String[] lines = {
                "<yellow>/clan criar (nome) (tag) <gray>- fundar um clan",
                "<yellow>/clan convidar (jogador) <gray>- recrutar (Oficial+)",
                "<yellow>/clan aceitar <dark_gray>/ <yellow>/clan recusar <gray>- responder convite",
                "<yellow>/clan info <dark_gray>/ <yellow>/clan lista <gray>- ver clans",
                "<yellow>/clan sair <dark_gray>/ <yellow>/clan expulsar (jogador)",
                "<yellow>/clan promover <dark_gray>/ <yellow>/clan rebaixar <dark_gray>/ <yellow>/clan transferir",
                "<yellow>/clan cor (cor) <dark_gray>/ <yellow>/clan ff|glow|simbolo (on/off) <gray>(Líder)",
                "<yellow>/clan banco (depositar/sacar) (valor)",
                "<yellow>/clan chat (msg) <dark_gray>/ <yellow>/c (msg) <gray>- chat do clan",
                "<yellow>/clan dissolver <gray>- apagar o clan (Líder)"
        };
        for (String l : lines) {
            player.sendMessage(plugin.getMessages().parse(l));
        }
    }

    private String onOff(boolean v) {
        return v ? "on" : "off";
    }

    private void usage(Player player, String u) {
        player.sendMessage(Component.text("Uso: " + u, NamedTextColor.GRAY));
    }

    private Long parseAmount(Player player, String raw) {
        try {
            long v = Long.parseLong(raw);
            if (v <= 0) {
                player.sendMessage(Component.text("O valor precisa ser positivo.", NamedTextColor.RED));
                return null;
            }
            return v;
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Valor inválido: " + raw, NamedTextColor.RED));
            return null;
        }
    }

    private String join(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(SUB, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "convidar", "invite", "expulsar", "kick", "promover", "promote",
                     "rebaixar", "demote", "transferir", "transfer" -> {
                    return onlinePlayers(args[1]);
                }
                case "ff", "friendlyfire", "glow", "simbolo", "symbol" -> {
                    return filter(TOGGLE, args[1]);
                }
                case "banco", "bank" -> {
                    return filter(BANK_SUB, args[1]);
                }
                case "cor", "color" -> {
                    return filter(clans().config().allowedColors(), args[1]);
                }
                default -> {
                    return List.of();
                }
            }
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
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
