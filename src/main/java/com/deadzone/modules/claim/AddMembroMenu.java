package com.deadzone.modules.claim;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Escolher um jogador online para adicionar como membro. */
public class AddMembroMenu extends Menu {

    private final DeadzonePlugin plugin;
    private final ClaimManager manager;
    private final Claim claim;

    public AddMembroMenu(DeadzonePlugin plugin, ClaimManager manager, Claim claim) {
        this.plugin = plugin;
        this.manager = manager;
        this.claim = claim;
    }

    @Override
    public Component title() {
        return Component.text("Adicionar Membro", NamedTextColor.DARK_GREEN);
    }

    @Override
    public int size() {
        return 54;
    }

    @Override
    protected void build(Player viewer) {
        if (!claim.owner().equals(viewer.getUniqueId())) {
            viewer.closeInventory();
            viewer.sendActionBar(Component.text("Apenas o dono da base pode fazer isso.", NamedTextColor.RED));
            return;
        }
        int slot = 0;
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.getUniqueId().equals(claim.owner()) || claim.isMember(online.getUniqueId())) {
                continue;
            }
            if (slot >= 45) {
                break;
            }
            setItem(slot, ClaimIcons.head(online, Component.text(online.getName(), NamedTextColor.WHITE),
                            List.of(Component.text("Clique para convidar", NamedTextColor.YELLOW))),
                    e -> {
                        manager.addMember(claim, online.getUniqueId());
                        viewer.sendActionBar(Component.text(online.getName() + " adicionado à base.",
                                NamedTextColor.GREEN));
                        new MembrosMenu(plugin, manager, claim).open(viewer);
                    });
            slot++;
        }
        setItem(45, ClaimIcons.item(Material.ARROW, Component.text("Voltar", NamedTextColor.GRAY)),
                e -> new MembrosMenu(plugin, manager, claim).open(viewer));
        fillEmpty(ClaimIcons.filler());
    }
}
