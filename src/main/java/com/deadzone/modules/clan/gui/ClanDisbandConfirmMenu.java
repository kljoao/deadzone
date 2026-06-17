package com.deadzone.modules.clan.gui;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import com.deadzone.modules.clan.Clan;
import com.deadzone.modules.clan.ClanManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Confirmação de dissolução do clã, avisando que o cofre será perdido para sempre. */
public class ClanDisbandConfirmMenu extends Menu {

    private final DeadzonePlugin plugin;

    public ClanDisbandConfirmMenu(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    private ClanManager cm() {
        return plugin.getClanManager();
    }

    @Override
    public Component title() {
        return Component.text("Dissolver o Clã?", NamedTextColor.DARK_RED);
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    protected void build(Player viewer) {
        Clan clan = cm().getClanOf(viewer.getUniqueId());
        if (clan == null || !clan.leader().equals(viewer.getUniqueId())) {
            setItem(13, ClanGui.item(plugin, Material.BARRIER, "<red>Indisponível",
                    "<gray>Só o líder pode dissolver."));
            setItem(22, ClanGui.item(plugin, Material.ARROW, "<yellow>Voltar"),
                    e -> new ClanSettingsMenu(plugin).open(viewer));
            fillEmpty(ClanGui.filler());
            return;
        }

        setItem(13, ClanGui.item(plugin, Material.TNT, "<red><bold>ATENÇÃO",
                "<gray>Você vai <red>apagar o clã <gray>" + clan.name() + ".",
                "<red>O cofre de <gold>" + plugin.getEconomyManager().format(clan.bank())
                        + " <red>será PERDIDO para sempre.",
                "<gray>Todos os integrantes serão removidos.",
                "<dark_red>Esta ação é irreversível."));

        setItem(11, ClanGui.item(plugin, Material.RED_WOOL, "<red><bold>SIM, DISSOLVER",
                        "<gray>Apagar o clã e o cofre."),
                e -> {
                    cm().disband(viewer);
                    viewer.closeInventory();
                });

        setItem(15, ClanGui.item(plugin, Material.LIME_WOOL, "<green>Cancelar",
                        "<gray>Manter o clã."),
                e -> new ClanSettingsMenu(plugin).open(viewer));

        fillEmpty(ClanGui.filler());
    }
}
