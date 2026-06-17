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

/** Lista (leitura) dos clãs do servidor. */
public class ClanListMenu extends Menu {

    private final DeadzonePlugin plugin;

    public ClanListMenu(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    private ClanManager cm() {
        return plugin.getClanManager();
    }

    @Override
    public Component title() {
        return Component.text("Clãs do Servidor", NamedTextColor.DARK_AQUA);
    }

    @Override
    public int size() {
        return 54;
    }

    @Override
    protected void build(Player viewer) {
        int slot = 0;
        for (Clan clan : cm().all()) {
            if (slot >= 45) {
                break;
            }
            ClanMember leader = clan.member(clan.leader());
            String leaderName = leader != null ? leader.lastKnownName() : "?";
            setItem(slot++, ClanGui.item(plugin, Material.SHIELD,
                    "<" + clan.color().toLowerCase() + ">[" + clan.tag() + "] " + clan.name(),
                    "<gray>Líder: <white>" + leaderName,
                    "<gray>Integrantes: <white>" + clan.size() + "/" + cm().maxMembersFor(clan),
                    "<gray>Tier: <white>" + clan.level()));
        }
        if (cm().all().isEmpty()) {
            setItem(22, ClanGui.item(plugin, Material.BARRIER, "<gray>Nenhum clã fundado ainda."));
        }
        setItem(49, ClanGui.item(plugin, Material.ARROW, "<yellow>Voltar"),
                e -> new ClanMenu(plugin).open(viewer));
        fillEmpty(ClanGui.filler());
    }
}
