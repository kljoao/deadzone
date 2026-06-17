package com.deadzone.modules.clan.gui;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import com.deadzone.modules.clan.Clan;
import com.deadzone.modules.clan.ClanManager;
import com.deadzone.modules.clan.ClanRole;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Menu raiz dos clans: criar/listar (sem clan) ou painel de gestão (com clan). */
public class ClanMenu extends Menu {

    private final DeadzonePlugin plugin;

    public ClanMenu(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    private ClanManager cm() {
        return plugin.getClanManager();
    }

    @Override
    public Component title() {
        return Component.text("Clãs", NamedTextColor.DARK_AQUA);
    }

    @Override
    public int size() {
        return 45;
    }

    @Override
    protected void build(Player viewer) {
        Clan clan = cm().getClanOf(viewer.getUniqueId());
        if (clan == null) {
            buildNoClan(viewer);
        } else {
            buildDashboard(viewer, clan);
        }
        fillEmpty(ClanGui.filler());
    }

    private void buildNoClan(Player viewer) {
        setItem(20, ClanGui.item(plugin, Material.NETHER_STAR, "<green><bold>Fundar um Clã",
                        "<gray>Crie seu próprio clã.",
                        "<gray>Custo: <gold>" + plugin.getEconomyManager().format(cm().config().createCost()),
                        "",
                        "<green>▶ Clique para criar"),
                e -> startCreate(viewer));
        setItem(24, ClanGui.item(plugin, Material.BOOK, "<yellow>Lista de Clãs",
                        "<gray>Veja os clãs do servidor."),
                e -> new ClanListMenu(plugin).open(viewer));
    }

    private void startCreate(Player viewer) {
        promptName(viewer);
    }

    private void promptName(Player viewer) {
        cm().prompts().await(viewer, "<yellow>Digite o <bold>NOME</bold> do clã:", input -> {
            String name = input.trim();
            String err = cm().nameError(name);
            if (err != null) {
                viewer.sendMessage(Component.text("✖ " + err, NamedTextColor.RED));
                promptName(viewer); // pergunta de novo
                return;
            }
            viewer.sendMessage(Component.text("✔ Nome: ", NamedTextColor.GREEN)
                    .append(Component.text(name, NamedTextColor.WHITE)));
            promptTag(viewer, name);
        });
    }

    private void promptTag(Player viewer, String name) {
        String hint = "<yellow>Agora a <bold>TAG</bold> do clã ("
                + cm().config().tagMin() + "-" + cm().config().tagMax() + " caracteres):";
        cm().prompts().await(viewer, hint, input -> {
            String tag = input.trim();
            String err = cm().tagError(tag);
            if (err != null) {
                viewer.sendMessage(Component.text("✖ " + err, NamedTextColor.RED));
                promptTag(viewer, name); // pergunta de novo
                return;
            }
            viewer.sendMessage(Component.text("✔ Tag: ", NamedTextColor.GREEN)
                    .append(Component.text("[" + tag + "]", NamedTextColor.WHITE)));
            new ClanCreateConfirmMenu(plugin, name, tag).open(viewer);
        });
    }

    private void buildDashboard(Player viewer, Clan clan) {
        ClanRole role = clan.roleOf(viewer.getUniqueId());
        boolean officer = role != null && role.canManageMembers();
        boolean leader = clan.leader().equals(viewer.getUniqueId());

        String tierLine = cm().isMaxTier(clan)
                ? "<gray>Tier <white>" + clan.level() + " <dark_gray>(máx)"
                : "<gray>Tier <white>" + clan.level() + " <dark_gray>→ <gray>próx: <white>"
                        + cm().nextTierMembers(clan) + " vagas";
        setItem(4, ClanGui.item(plugin, Material.CYAN_BANNER,
                "<" + clan.color().toLowerCase() + ">[" + clan.tag() + "] " + clan.name(),
                "<gray>Integrantes: <white>" + clan.size() + "/" + cm().maxMembersFor(clan),
                tierLine,
                "<gray>Cofre: <gold>" + plugin.getEconomyManager().format(clan.bank()),
                "<gray>Seu cargo: <white>" + (role != null ? role.displayName() : "-")));

        setItem(19, ClanGui.item(plugin, Material.PLAYER_HEAD, "<aqua>Integrantes",
                        "<gray>Veja e gerencie os membros."),
                e -> new ClanMembersMenu(plugin).open(viewer));

        setItem(21, ClanGui.item(plugin, Material.GOLD_INGOT, "<gold>Cofre",
                        "<gray>Saldo: <gold>" + plugin.getEconomyManager().format(clan.bank()),
                        "<gray>Depositar/sacar scraps."),
                e -> new ClanBankMenu(plugin).open(viewer));

        String evoluirLore = cm().isMaxTier(clan) ? "<dark_gray>Tier máximo atingido."
                : "<gray>Próx: <white>" + cm().nextTierMembers(clan) + " vagas <gray>por <gold>"
                        + plugin.getEconomyManager().format(cm().nextTierCost(clan));
        setItem(23, ClanGui.item(plugin, Material.EXPERIENCE_BOTTLE, "<green>Evoluir Tier",
                        "<gray>Tier atual: <white>" + clan.level(), evoluirLore),
                e -> new ClanTierMenu(plugin).open(viewer));

        if (officer) {
            setItem(25, ClanGui.item(plugin, Material.WRITABLE_BOOK, "<yellow>Convidar Jogador",
                            "<gray>Recrutar alguém online."),
                    e -> cm().prompts().await(viewer, "<yellow>Digite o NOME do jogador a convidar:", target -> {
                        cm().invite(viewer, target);
                        new ClanMenu(plugin).open(viewer);
                    }));
        }

        if (leader) {
            setItem(31, ClanGui.item(plugin, Material.COMPARATOR, "<light_purple>Configurações",
                            "<gray>Toggles, cor e dissolver."),
                    e -> new ClanSettingsMenu(plugin).open(viewer));
        }

        setItem(39, ClanGui.item(plugin, Material.BOOK, "<yellow>Lista de Clãs"),
                e -> new ClanListMenu(plugin).open(viewer));

        setItem(41, ClanGui.item(plugin, Material.BARRIER, "<red>Sair do Clã",
                        "<gray>" + (leader ? "Você é o líder: transfira ou dissolva." : "Deixar este clã.")),
                e -> {
                    cm().leave(viewer);
                    new ClanMenu(plugin).open(viewer);
                });
    }
}
