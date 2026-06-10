package com.deadzone.modules.claim;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Menu principal da base (aberto pelo núcleo). */
public class BaseMenu extends Menu {

    private final DeadzonePlugin plugin;
    private final ClaimManager manager;
    private final Claim claim;

    public BaseMenu(DeadzonePlugin plugin, ClaimManager manager, Claim claim) {
        this.plugin = plugin;
        this.manager = manager;
        this.claim = claim;
    }

    @Override
    public Component title() {
        return Component.text("☗ Sua Base", NamedTextColor.DARK_GREEN);
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    protected void build(Player viewer) {
        setItem(11, ClaimIcons.head(Bukkit.getOfflinePlayer(claim.owner()),
                        Component.text("Membros", NamedTextColor.GREEN),
                        java.util.List.of(
                                Component.text("Convide jogadores e defina", NamedTextColor.GRAY),
                                Component.text("as permissões de cada um.", NamedTextColor.GRAY))),
                e -> new MembrosMenu(plugin, manager, claim).open(viewer));

        int width = claim.maxX() - claim.minX() + 1;
        int depth = claim.maxZ() - claim.minZ() + 1;
        setItem(13, ClaimIcons.item(Material.FILLED_MAP,
                Component.text("Informações", NamedTextColor.AQUA),
                Component.text("Tamanho: " + width + "x" + depth, NamedTextColor.GRAY),
                Component.text("Altura: " + claim.minY() + " a " + claim.maxY(), NamedTextColor.GRAY),
                Component.text("Membros: " + claim.members().size(), NamedTextColor.GRAY),
                Component.text("Mundo: " + claim.world(), NamedTextColor.DARK_GRAY)));

        setItem(15, ClaimIcons.item(Material.ANVIL,
                        Component.text("Evoluir base", NamedTextColor.GOLD),
                        Component.text("Subir altura e baús.", NamedTextColor.GRAY),
                        Component.text("Em breve (Fase 3).", NamedTextColor.DARK_GRAY)),
                e -> viewer.sendActionBar(Component.text("Evolução chega na Fase 3.", NamedTextColor.GRAY)));

        fillEmpty(ClaimIcons.filler());
    }
}
