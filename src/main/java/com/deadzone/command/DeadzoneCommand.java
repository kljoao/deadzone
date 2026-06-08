package com.deadzone.command;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.Messages;
import com.deadzone.core.debug.TestMenu;
import com.deadzone.core.item.CustomItem;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.modules.events.mutant.MutantType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Comando administrativo/debug raiz. Tudo aqui exige a permissão deadzone.admin.
 */
public class DeadzoneCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("info", "reload", "profile", "giveitem", "menu", "infection", "skill", "xp", "lock", "unlock", "zone", "bloodmoon", "mutant");
    private static final List<String> MUTANT_TYPES = List.of("RUNNER", "TANK", "EXPLODER");
    private static final List<String> INFECTION_ACTIONS = List.of("get", "set", "infect");
    private static final List<String> SKILL_ACTIONS = List.of("add", "remove");
    private static final List<String> ZONE_ACTIONS = List.of("pos1", "pos2", "create", "remove", "list");
    private static final List<String> BLOODMOON_ACTIONS = List.of("start", "stop", "status");

    private final DeadzonePlugin plugin;

    public DeadzoneCommand(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        Messages msg = plugin.getMessages();

        if (!sender.hasPermission("deadzone.admin")) {
            msg.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            msg.send(sender, "unknown-subcommand");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info" -> info(sender);
            case "reload" -> reload(sender);
            case "profile" -> profile(sender, args);
            case "giveitem" -> giveItem(sender, args);
            case "menu" -> menu(sender);
            case "infection" -> infection(sender, args);
            case "skill" -> skill(sender, args);
            case "xp" -> xp(sender, args);
            case "lock" -> lockContainer(sender, true);
            case "unlock" -> lockContainer(sender, false);
            case "zone" -> zone(sender, args);
            case "bloodmoon" -> bloodmoon(sender, args);
            case "mutant" -> mutant(sender, args);
            default -> msg.send(sender, "unknown-subcommand");
        }
        return true;
    }

    private void info(CommandSender sender) {
        sender.sendMessage(plugin.getMessages().prefix().append(
                Component.text("v" + plugin.getPluginMeta().getVersion()
                        + " — perfis em cache: " + plugin.getProfileManager().cached().size(),
                        NamedTextColor.GREEN)));
    }

    private void reload(CommandSender sender) {
        plugin.getConfigManager().load();
        plugin.getMessages().load();
        plugin.getInfectionConfig().load();
        plugin.getWorldManager().reload();
        plugin.getMedicineManager().reload();
        plugin.getClassManager().reload();
        plugin.getSanityManager().reload();
        plugin.getEventsManager().reload();
        plugin.getMessages().send(sender, "reload-success");
    }

    private void infection(CommandSender sender, String[] args) {
        Messages msg = plugin.getMessages();
        if (args.length < 3) {
            sender.sendMessage(Component.text("Uso: /deadzone infection <get|set|infect> <jogador> [valor]", NamedTextColor.YELLOW));
            return;
        }
        String action = args[1].toLowerCase();
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            msg.send(sender, "player-not-found");
            return;
        }
        PlayerProfile p = plugin.getProfileManager().get(target.getUniqueId());
        if (p == null) {
            msg.send(sender, "profile-not-loaded");
            return;
        }
        switch (action) {
            case "get" -> sender.sendMessage(Component.text(
                    target.getName() + " — infectado: " + p.isInfected()
                            + " (" + String.format("%.1f", p.getInfectionLevel()) + "%)", NamedTextColor.GRAY));
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Uso: /deadzone infection set <jogador> <0-100>", NamedTextColor.YELLOW));
                    return;
                }
                double value;
                try {
                    value = Double.parseDouble(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Valor inválido.", NamedTextColor.RED));
                    return;
                }
                plugin.getInfectionManager().setLevel(p, value);
                sender.sendMessage(Component.text("Infecção de " + target.getName() + " definida para "
                        + String.format("%.1f", p.getInfectionLevel()) + "%.", NamedTextColor.GREEN));
            }
            case "infect" -> {
                plugin.getInfectionManager().infect(p, target);
                sender.sendMessage(Component.text(target.getName() + " foi infectado.", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Ação desconhecida. Use get|set|infect.", NamedTextColor.RED));
        }
    }

    private void profile(CommandSender sender, String[] args) {
        Messages msg = plugin.getMessages();
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /deadzone profile <jogador>", NamedTextColor.YELLOW));
            return;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg.send(sender, "player-not-found");
            return;
        }
        PlayerProfile p = plugin.getProfileManager().get(target.getUniqueId());
        if (p == null) {
            msg.send(sender, "profile-not-loaded");
            return;
        }
        sender.sendMessage(Component.text("── Perfil de " + p.getLastKnownName() + " ──", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Classe: " + p.getPlayerClass(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Infectado: " + p.isInfected()
                + " (" + String.format("%.1f", p.getInfectionLevel()) + "%)", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Sanidade: " + String.format("%.1f", p.getSanity()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("XP: " + p.getXp() + " (total: " + p.getTotalXpEarned() + ")", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Skills: " + p.getUnlockedSkills().size(), NamedTextColor.GRAY));
    }

    private void giveItem(CommandSender sender, String[] args) {
        Messages msg = plugin.getMessages();
        if (!(sender instanceof Player player)) {
            msg.send(sender, "player-only");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /deadzone giveitem <id>", NamedTextColor.YELLOW));
            return;
        }
        CustomItem item = plugin.getItemRegistry().get(args[1].toLowerCase());
        if (item == null) {
            msg.send(sender, "item-unknown", "item", args[1]);
            return;
        }
        player.getInventory().addItem(item.build());
        msg.send(sender, "item-given", "item", item.id());
    }

    private void skill(CommandSender sender, String[] args) {
        Messages msg = plugin.getMessages();
        if (args.length < 4) {
            sender.sendMessage(Component.text("Uso: /deadzone skill <add|remove> <jogador> <skillId>", NamedTextColor.YELLOW));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            msg.send(sender, "player-not-found");
            return;
        }
        PlayerProfile p = plugin.getProfileManager().get(target.getUniqueId());
        if (p == null) {
            msg.send(sender, "profile-not-loaded");
            return;
        }
        String skillId = args[3];
        switch (args[1].toLowerCase()) {
            case "add" -> {
                p.unlockSkill(skillId);
                sender.sendMessage(Component.text("Skill '" + skillId + "' concedida a " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "remove" -> {
                p.lockSkill(skillId);
                sender.sendMessage(Component.text("Skill '" + skillId + "' removida de " + target.getName() + ".", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Ação desconhecida. Use add|remove.", NamedTextColor.RED));
        }
    }

    private void xp(CommandSender sender, String[] args) {
        Messages msg = plugin.getMessages();
        if (args.length < 3) {
            sender.sendMessage(Component.text("Uso: /deadzone xp <jogador> <quantidade>", NamedTextColor.YELLOW));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg.send(sender, "player-not-found");
            return;
        }
        PlayerProfile p = plugin.getProfileManager().get(target.getUniqueId());
        if (p == null) {
            msg.send(sender, "profile-not-loaded");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Quantidade inválida.", NamedTextColor.RED));
            return;
        }
        if (amount <= 0) {
            sender.sendMessage(Component.text("A quantidade deve ser maior que zero.", NamedTextColor.RED));
            return;
        }
        plugin.getClassManager().grantXp(target, p, amount, "admin");
        sender.sendMessage(Component.text("Concedido " + amount + " XP a " + target.getName()
                + " (total: " + p.getXp() + ").", NamedTextColor.GREEN));
    }

    private void lockContainer(CommandSender sender, boolean locked) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return;
        }
        Block block = player.getTargetBlockExact(5);
        if (block == null) {
            player.sendMessage(Component.text("Mire em um contêiner (até 5 blocos).", NamedTextColor.YELLOW));
            return;
        }
        boolean ok = plugin.getClassManager().saqueador().setLocked(block, locked);
        if (!ok) {
            player.sendMessage(Component.text("Esse bloco não pode ser trancado.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text(locked ? "Contêiner trancado." : "Contêiner destrancado.", NamedTextColor.GREEN));
    }

    private void zone(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase() : "list";
        var zones = plugin.getEventsManager().zone();
        switch (action) {
            case "pos1" -> {
                zones.setPos(player, 0);
                player.sendMessage(Component.text("Pos1 definida.", NamedTextColor.GREEN));
            }
            case "pos2" -> {
                zones.setPos(player, 1);
                player.sendMessage(Component.text("Pos2 definida.", NamedTextColor.GREEN));
            }
            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Uso: /deadzone zone create <nome> [dps]", NamedTextColor.YELLOW));
                    return;
                }
                double dps = 2.0;
                if (args.length >= 4) {
                    try {
                        dps = Double.parseDouble(args[3]);
                    } catch (NumberFormatException ignored) {
                        // mantém o padrão
                    }
                }
                if (zones.createZone(player, args[2], dps)) {
                    player.sendMessage(Component.text("Zona '" + args[2] + "' criada (dps=" + dps + ").", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Defina pos1 e pos2 primeiro.", NamedTextColor.RED));
                }
            }
            case "remove" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Uso: /deadzone zone remove <nome>", NamedTextColor.YELLOW));
                    return;
                }
                boolean ok = zones.removeZone(args[2]);
                player.sendMessage(Component.text(ok ? "Zona removida." : "Zona não encontrada.",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            case "list" -> {
                if (zones.config().zones().isEmpty()) {
                    player.sendMessage(Component.text("Nenhuma zona definida.", NamedTextColor.GRAY));
                } else {
                    player.sendMessage(Component.text("Zonas:", NamedTextColor.GOLD));
                    zones.config().zones().forEach(z -> player.sendMessage(
                            Component.text("• " + z.name() + " (" + z.world() + ", dps=" + z.damagePerSecond() + ")", NamedTextColor.GRAY)));
                }
            }
            default -> player.sendMessage(Component.text("Uso: /deadzone zone <pos1|pos2|create|remove|list>", NamedTextColor.YELLOW));
        }
    }

    private void mutant(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /deadzone mutant <RUNNER|TANK|EXPLODER> [quantidade]", NamedTextColor.YELLOW));
            return;
        }
        MutantType type = MutantType.fromString(args[1]);
        if (type == null) {
            sender.sendMessage(Component.text("Tipo inválido. Use RUNNER, TANK ou EXPLODER.", NamedTextColor.RED));
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(20, Integer.parseInt(args[2])));
            } catch (NumberFormatException ignored) {
                // mantém 1
            }
        }
        for (int i = 0; i < amount; i++) {
            var entity = player.getWorld().spawnEntity(player.getLocation(), EntityType.ZOMBIE,
                    CreatureSpawnEvent.SpawnReason.COMMAND);
            if (entity instanceof LivingEntity living) {
                plugin.getEventsManager().mutant().applyType(living, type);
            }
        }
        sender.sendMessage(Component.text("Spawnados " + amount + " zumbi(s) do tipo " + type.name() + ".", NamedTextColor.GREEN));
    }

    private void bloodmoon(CommandSender sender, String[] args) {
        var bm = plugin.getEventsManager().bloodMoon();
        String action = args.length > 1 ? args[1].toLowerCase() : "status";
        switch (action) {
            case "start" -> {
                bm.forceStart();
                sender.sendMessage(Component.text("Lua de Sangue iniciada.", NamedTextColor.DARK_RED));
            }
            case "stop" -> {
                bm.forceStop();
                sender.sendMessage(Component.text("Lua de Sangue encerrada.", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Lua de Sangue ativa: " + bm.isActive(), NamedTextColor.GRAY));
        }
    }

    private void menu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return;
        }
        new TestMenu().open(player);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("deadzone.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("giveitem")) {
            List<String> ids = new ArrayList<>();
            plugin.getItemRegistry().all().forEach(i -> ids.add(i.id()));
            return filter(ids, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("profile") || args[0].equalsIgnoreCase("xp"))) {
            return filter(onlineNames(), args[1]);
        }
        if (args[0].equalsIgnoreCase("infection")) {
            if (args.length == 2) {
                return filter(INFECTION_ACTIONS, args[1]);
            }
            if (args.length == 3) {
                return filter(onlineNames(), args[2]);
            }
        }
        if (args[0].equalsIgnoreCase("zone") && args.length == 2) {
            return filter(ZONE_ACTIONS, args[1]);
        }
        if (args[0].equalsIgnoreCase("bloodmoon") && args.length == 2) {
            return filter(BLOODMOON_ACTIONS, args[1]);
        }
        if (args[0].equalsIgnoreCase("mutant") && args.length == 2) {
            return filter(MUTANT_TYPES, args[1]);
        }
        if (args[0].equalsIgnoreCase("skill")) {
            if (args.length == 2) {
                return filter(SKILL_ACTIONS, args[1]);
            }
            if (args.length == 3) {
                return filter(onlineNames(), args[2]);
            }
            if (args.length == 4) {
                return filter(List.of("med_farmacologia_avancada", "med_diagnostico_rapido",
                        "med_reanimacao", "bru_resistencia_viral", "bru_atordoamento",
                        "saq_sexto_sentido", "saq_lockpicking"), args[3]);
            }
        }
        return List.of();
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
