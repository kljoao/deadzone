package com.deadzone.modules.claim;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Permissões de um membro específico (quebrar, baús, portas) + remover. */
public class MembroPermMenu extends Menu {

    private final DeadzonePlugin plugin;
    private final ClaimManager manager;
    private final Claim claim;
    private final UUID member;

    public MembroPermMenu(DeadzonePlugin plugin, ClaimManager manager, Claim claim, UUID member) {
        this.plugin = plugin;
        this.manager = manager;
        this.claim = claim;
        this.member = member;
    }

    @Override
    public Component title() {
        OfflinePlayer op = Bukkit.getOfflinePlayer(member);
        String name = op.getName() != null ? op.getName() : "membro";
        return Component.text("Permissões: " + name, NamedTextColor.DARK_GREEN);
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    protected void build(Player viewer) {
        if (!claim.owner().equals(viewer.getUniqueId())) {
            viewer.closeInventory();
            viewer.sendActionBar(Component.text("Apenas o dono da base pode fazer isso.", NamedTextColor.RED));
            return;
        }
        setItem(10, toggle("Quebrar/colocar blocos", Claim.FLAG_BUILD),
                e -> {
                    manager.toggleFlag(claim, member, Claim.FLAG_BUILD);
                    refresh(viewer);
                });
        setItem(12, toggle("Abrir baús", Claim.FLAG_CONTAINERS),
                e -> {
                    manager.toggleFlag(claim, member, Claim.FLAG_CONTAINERS);
                    refresh(viewer);
                });
        setItem(14, toggle("Abrir portas", Claim.FLAG_DOORS),
                e -> {
                    manager.toggleFlag(claim, member, Claim.FLAG_DOORS);
                    refresh(viewer);
                });
        setItem(16, ClaimIcons.item(Material.BARRIER,
                        Component.text("Remover membro", NamedTextColor.RED),
                        Component.text("Tira o jogador da base.", NamedTextColor.GRAY)),
                e -> {
                    manager.removeMember(claim, member);
                    new MembrosMenu(plugin, manager, claim).open(viewer);
                });
        setItem(22, ClaimIcons.item(Material.ARROW, Component.text("Voltar", NamedTextColor.GRAY)),
                e -> new MembrosMenu(plugin, manager, claim).open(viewer));
        fillEmpty(ClaimIcons.filler());
    }

    private org.bukkit.inventory.ItemStack toggle(String label, String flag) {
        boolean on = claim.hasFlag(member, flag);
        return ClaimIcons.item(on ? Material.LIME_DYE : Material.GRAY_DYE,
                Component.text(label, on ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                Component.text(on ? "Ativado" : "Desativado", on ? NamedTextColor.GREEN : NamedTextColor.RED),
                Component.text("Clique para alternar", NamedTextColor.YELLOW));
    }
}
