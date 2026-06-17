package com.deadzone.modules.clan.gui;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import com.deadzone.modules.clan.Clan;
import com.deadzone.modules.clan.ClanConfig;
import com.deadzone.modules.clan.ClanManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Configurações do clã (somente líder): friendly-fire, glow, símbolo, cor e dissolver. */
public class ClanSettingsMenu extends Menu {

    private final DeadzonePlugin plugin;

    public ClanSettingsMenu(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    private ClanManager cm() {
        return plugin.getClanManager();
    }

    @Override
    public Component title() {
        return Component.text("Configurações do Clã", NamedTextColor.LIGHT_PURPLE);
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    protected void build(Player viewer) {
        Clan clan = cm().getClanOf(viewer.getUniqueId());
        if (clan == null || !clan.leader().equals(viewer.getUniqueId())) {
            setItem(26, ClanGui.item(plugin, Material.ARROW, "<yellow>Voltar"),
                    e -> new ClanMenu(plugin).open(viewer));
            fillEmpty(ClanGui.filler());
            return;
        }

        setItem(10, toggle(Material.IRON_SWORD, "Dano entre membros (FF)", clan.friendlyFire()),
                e -> {
                    cm().setFriendlyFire(viewer, !clan.friendlyFire());
                    refresh(viewer);
                });
        setItem(12, toggle(Material.GLOWSTONE_DUST, "Glow de aliado", clan.glow()),
                e -> {
                    cm().setGlow(viewer, !clan.glow());
                    refresh(viewer);
                });
        setItem(14, toggle(Material.NAME_TAG, "Símbolo de aliado", clan.symbol()),
                e -> {
                    cm().setSymbol(viewer, !clan.symbol());
                    refresh(viewer);
                });

        if (viewer.hasPermission(ClanConfig.PERM_COLORED_TAG)) {
            setItem(16, ClanGui.item(plugin, Material.CYAN_DYE, "<aqua>Cor da Tag",
                            "<gray>Cor atual: <" + clan.color().toLowerCase() + ">" + clan.color(),
                            "<gray>Escolher uma cor."),
                    e -> new ClanColorMenu(plugin).open(viewer));
        } else {
            setItem(16, ClanGui.item(plugin, Material.GRAY_DYE, "<dark_gray>Cor da Tag (VIP+)",
                    "<gray>Tag colorida é exclusiva de VIP+",
                    "<gray>(Warlord/Overlord)."));
        }

        setItem(22, ClanGui.item(plugin, Material.TNT, "<red>Dissolver Clã",
                        "<gray>Apaga o clã e o cofre permanentemente.",
                        "<gray>Pede confirmação."),
                e -> new ClanDisbandConfirmMenu(plugin).open(viewer));

        setItem(26, ClanGui.item(plugin, Material.ARROW, "<yellow>Voltar"),
                e -> new ClanMenu(plugin).open(viewer));
        fillEmpty(ClanGui.filler());
    }

    private org.bukkit.inventory.ItemStack toggle(Material mat, String label, boolean on) {
        return ClanGui.item(plugin, mat,
                (on ? "<green>" : "<red>") + label + ": " + (on ? "LIGADO" : "DESLIGADO"),
                "<gray>Clique para " + (on ? "desligar" : "ligar") + ".");
    }
}
