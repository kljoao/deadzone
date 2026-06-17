package com.deadzone.modules.clan.gui;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import com.deadzone.modules.clan.Clan;
import com.deadzone.modules.clan.ClanManager;
import com.deadzone.modules.clan.ClanMember;
import com.deadzone.modules.clan.ClanRole;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** Lista de integrantes do clã; clicar abre as ações (se você tiver cargo para gerenciar). */
public class ClanMembersMenu extends Menu {

    private final DeadzonePlugin plugin;

    public ClanMembersMenu(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    private ClanManager cm() {
        return plugin.getClanManager();
    }

    @Override
    public Component title() {
        return Component.text("Integrantes", NamedTextColor.DARK_AQUA);
    }

    @Override
    public int size() {
        return 54;
    }

    @Override
    protected void build(Player viewer) {
        Clan clan = cm().getClanOf(viewer.getUniqueId());
        if (clan == null) {
            setItem(49, ClanGui.item(plugin, Material.ARROW, "<yellow>Voltar"),
                    e -> new ClanMenu(plugin).open(viewer));
            fillEmpty(ClanGui.filler());
            return;
        }
        ClanRole viewerRole = clan.roleOf(viewer.getUniqueId());

        List<ClanMember> sorted = new ArrayList<>(clan.members());
        sorted.sort((a, b) -> Integer.compare(b.role().weight(), a.role().weight()));

        int slot = 0;
        for (ClanMember m : sorted) {
            if (slot >= 45) {
                break;
            }
            boolean online = Bukkit.getPlayer(m.uuid()) != null;
            boolean canManage = viewerRole != null && viewerRole.canManageMembers()
                    && viewerRole.isAbove(m.role()) && !m.uuid().equals(viewer.getUniqueId());
            setItem(slot++, head(m, online, canManage), canManage
                    ? e -> new ClanMemberMenu(plugin, m.uuid()).open(viewer)
                    : null);
        }

        setItem(49, ClanGui.item(plugin, Material.ARROW, "<yellow>Voltar"),
                e -> new ClanMenu(plugin).open(viewer));
        fillEmpty(ClanGui.filler());
    }

    private ItemStack head(ClanMember m, boolean online, boolean canManage) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(meta -> {
            if (meta instanceof SkullMeta skull) {
                skull.setOwningPlayer(Bukkit.getOfflinePlayer(m.uuid()));
            }
            meta.displayName(plugin.getMessages().parse(m.role().colorTag() + m.lastKnownName())
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.getMessages().parse("<gray>Cargo: <white>" + m.role().displayName())
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(plugin.getMessages().parse(online ? "<green>● Online" : "<dark_gray>● Offline")
                    .decoration(TextDecoration.ITALIC, false));
            if (canManage) {
                lore.add(Component.empty());
                lore.add(plugin.getMessages().parse("<yellow>▶ Clique para gerenciar")
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        });
        return head;
    }
}
