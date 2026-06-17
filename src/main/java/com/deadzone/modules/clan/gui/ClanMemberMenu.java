package com.deadzone.modules.clan.gui;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import com.deadzone.modules.clan.Clan;
import com.deadzone.modules.clan.ClanManager;
import com.deadzone.modules.clan.ClanMember;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Ações sobre um integrante específico (promover/rebaixar/expulsar/transferir). */
public class ClanMemberMenu extends Menu {

    private final DeadzonePlugin plugin;
    private final UUID targetUuid;

    public ClanMemberMenu(DeadzonePlugin plugin, UUID targetUuid) {
        this.plugin = plugin;
        this.targetUuid = targetUuid;
    }

    private ClanManager cm() {
        return plugin.getClanManager();
    }

    @Override
    public Component title() {
        return Component.text("Gerenciar Integrante", NamedTextColor.DARK_AQUA);
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    protected void build(Player viewer) {
        Clan clan = cm().getClanOf(viewer.getUniqueId());
        ClanMember target = clan != null ? clan.member(targetUuid) : null;
        if (clan == null || target == null) {
            back(viewer);
            fillEmpty(ClanGui.filler());
            return;
        }
        boolean leader = clan.leader().equals(viewer.getUniqueId());
        String name = target.lastKnownName();

        setItem(4, ClanGui.item(plugin, Material.NAME_TAG,
                "<white>" + name, "<gray>Cargo: <white>" + target.role().displayName()));

        if (leader) {
            setItem(10, ClanGui.item(plugin, Material.LIME_DYE, "<green>Promover",
                            "<gray>Sobe o cargo de " + name + "."),
                    e -> {
                        cm().promote(viewer, targetUuid);
                        new ClanMembersMenu(plugin).open(viewer);
                    });
            setItem(12, ClanGui.item(plugin, Material.ORANGE_DYE, "<gold>Rebaixar",
                            "<gray>Desce o cargo de " + name + "."),
                    e -> {
                        cm().demote(viewer, targetUuid);
                        new ClanMembersMenu(plugin).open(viewer);
                    });
            setItem(16, ClanGui.item(plugin, Material.GOLDEN_HELMET, "<yellow>Transferir Liderança",
                            "<gray>Torna " + name + " o novo líder.",
                            "<red>Você vira Oficial."),
                    e -> {
                        cm().transfer(viewer, targetUuid);
                        new ClanMenu(plugin).open(viewer);
                    });
        }

        setItem(14, ClanGui.item(plugin, Material.BARRIER, "<red>Expulsar",
                        "<gray>Remove " + name + " do clã."),
                e -> {
                    cm().kick(viewer, targetUuid);
                    new ClanMembersMenu(plugin).open(viewer);
                });

        back(viewer);
        fillEmpty(ClanGui.filler());
    }

    private void back(Player viewer) {
        setItem(22, ClanGui.item(plugin, Material.ARROW, "<yellow>Voltar"),
                e -> new ClanMembersMenu(plugin).open(viewer));
    }
}
