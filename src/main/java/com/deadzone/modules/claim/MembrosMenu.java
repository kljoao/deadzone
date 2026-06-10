package com.deadzone.modules.claim;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Lista de membros da base + botão de adicionar. */
public class MembrosMenu extends Menu {

    private final DeadzonePlugin plugin;
    private final ClaimManager manager;
    private final Claim claim;

    public MembrosMenu(DeadzonePlugin plugin, ClaimManager manager, Claim claim) {
        this.plugin = plugin;
        this.manager = manager;
        this.claim = claim;
    }

    @Override
    public Component title() {
        return Component.text("Membros da Base", NamedTextColor.DARK_GREEN);
    }

    @Override
    public int size() {
        return 54;
    }

    @Override
    protected void build(Player viewer) {
        int slot = 0;
        for (UUID member : claim.members().keySet()) {
            if (slot >= 45) {
                break;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayer(member);
            String name = op.getName() != null ? op.getName() : member.toString().substring(0, 8);
            setItem(slot, ClaimIcons.head(op, Component.text(name, NamedTextColor.WHITE), List.of(
                            flag("Quebrar", claim.hasFlag(member, Claim.FLAG_BUILD)),
                            flag("Baús", claim.hasFlag(member, Claim.FLAG_CONTAINERS)),
                            flag("Portas", claim.hasFlag(member, Claim.FLAG_DOORS)),
                            Component.empty(),
                            Component.text("Clique para gerenciar", NamedTextColor.YELLOW))),
                    e -> new MembroPermMenu(plugin, manager, claim, member).open(viewer));
            slot++;
        }

        setItem(49, ClaimIcons.item(Material.EMERALD,
                        Component.text("Adicionar membro", NamedTextColor.GREEN),
                        Component.text("Convide um jogador online.", NamedTextColor.GRAY)),
                e -> new AddMembroMenu(plugin, manager, claim).open(viewer));
        setItem(45, ClaimIcons.item(Material.ARROW, Component.text("Voltar", NamedTextColor.GRAY)),
                e -> new BaseMenu(plugin, manager, claim).open(viewer));
        fillEmpty(ClaimIcons.filler());
    }

    private Component flag(String label, boolean on) {
        return Component.text((on ? "✔ " : "✘ ") + label, on ? NamedTextColor.GREEN : NamedTextColor.RED);
    }
}
