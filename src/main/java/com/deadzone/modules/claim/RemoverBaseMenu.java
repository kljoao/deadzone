package com.deadzone.modules.claim;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Confirmação para remover a base. */
public class RemoverBaseMenu extends Menu {

    private final DeadzonePlugin plugin;
    private final ClaimManager manager;
    private final Claim claim;

    public RemoverBaseMenu(DeadzonePlugin plugin, ClaimManager manager, Claim claim) {
        this.plugin = plugin;
        this.manager = manager;
        this.claim = claim;
    }

    @Override
    public Component title() {
        return Component.text("Remover a base?", NamedTextColor.DARK_RED);
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    protected void build(Player viewer) {
        setItem(11, ClaimIcons.item(Material.LIME_CONCRETE,
                        Component.text("Confirmar remoção", NamedTextColor.GREEN),
                        Component.text("Apaga a construção, os baús,", NamedTextColor.GRAY),
                        Component.text("aprimoramentos e TODOS os itens.", NamedTextColor.GRAY),
                        Component.text("Você recebe um Livro de Base.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Não dá para desfazer!", NamedTextColor.RED)),
                e -> manager.removeClaim(viewer, claim));

        setItem(15, ClaimIcons.item(Material.RED_CONCRETE,
                        Component.text("Cancelar", NamedTextColor.RED),
                        Component.text("Mantém a base.", NamedTextColor.GRAY)),
                e -> new BaseMenu(plugin, manager, claim).open(viewer));

        fillEmpty(ClaimIcons.filler());
    }
}
