package com.deadzone.modules.clan.gui;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import com.deadzone.modules.clan.ClanManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Confirmação final da criação do clã (nome + tag + custo). */
public class ClanCreateConfirmMenu extends Menu {

    private final DeadzonePlugin plugin;
    private final String name;
    private final String tag;

    public ClanCreateConfirmMenu(DeadzonePlugin plugin, String name, String tag) {
        this.plugin = plugin;
        this.name = name;
        this.tag = tag;
    }

    private ClanManager cm() {
        return plugin.getClanManager();
    }

    @Override
    public Component title() {
        return Component.text("Confirmar Criação", NamedTextColor.DARK_AQUA);
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    protected void build(Player viewer) {
        long cost = cm().config().createCost();
        setItem(13, ClanGui.item(plugin, Material.NETHER_STAR, "<aqua>Novo Clã",
                "<gray>Nome: <white>" + name,
                "<gray>Tag: <white>[" + tag + "]",
                "<gray>Custo: <gold>" + plugin.getEconomyManager().format(cost),
                "<gray>Seu saldo: <gold>" + plugin.getEconomyManager().format(plugin.getEconomyManager().balanceOf(viewer))));

        setItem(11, ClanGui.item(plugin, Material.LIME_WOOL, "<green><bold>CONFIRMAR",
                        "<gray>Criar o clã e pagar " + plugin.getEconomyManager().format(cost) + "."),
                e -> {
                    cm().create(viewer, name, tag);
                    new ClanMenu(plugin).open(viewer); // dashboard se criou; senão volta ao início
                });

        setItem(15, ClanGui.item(plugin, Material.RED_WOOL, "<red>Cancelar",
                        "<gray>Não criar o clã."),
                e -> new ClanMenu(plugin).open(viewer));

        fillEmpty(ClanGui.filler());
    }
}
