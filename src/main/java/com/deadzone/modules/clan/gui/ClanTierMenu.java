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

/** Progressão de tiers do clã: mostra a escada e permite evoluir (líder, pago pelo cofre). */
public class ClanTierMenu extends Menu {

    private final DeadzonePlugin plugin;

    public ClanTierMenu(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    private ClanManager cm() {
        return plugin.getClanManager();
    }

    @Override
    public Component title() {
        return Component.text("Tiers do Clã", NamedTextColor.GREEN);
    }

    @Override
    public int size() {
        return 45;
    }

    @Override
    protected void build(Player viewer) {
        Clan clan = cm().getClanOf(viewer.getUniqueId());
        if (clan == null) {
            setItem(40, ClanGui.item(plugin, Material.ARROW, "<yellow>Voltar"),
                    e -> new ClanMenu(plugin).open(viewer));
            fillEmpty(ClanGui.filler());
            return;
        }
        boolean leader = clan.leader().equals(viewer.getUniqueId());
        int current = (int) clan.level();

        int slot = 9;
        for (ClanConfig.Tier tier : cm().config().tiers().values()) {
            if (slot > 17) {
                break;
            }
            boolean done = tier.level() <= current;
            Material mat = tier.level() == current ? Material.NETHERITE_INGOT
                    : done ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            String marker = tier.level() == current ? " <gold>(atual)" : done ? " <green>✔" : "";
            setItem(slot++, ClanGui.item(plugin, mat, "<white>Tier " + tier.level() + marker,
                    "<gray>Vagas: <white>" + tier.members(),
                    "<gray>Custo p/ subir: <gold>" + plugin.getEconomyManager().format(tier.cost())));
        }

        if (cm().isMaxTier(clan)) {
            setItem(31, ClanGui.item(plugin, Material.BEACON, "<gold>Tier máximo atingido!",
                    "<gray>Seu clã está no topo."));
        } else {
            long cost = cm().nextTierCost(clan);
            String lore2 = "<gray>Cofre: <gold>" + plugin.getEconomyManager().format(clan.bank());
            if (leader) {
                setItem(31, ClanGui.item(plugin, Material.EXPERIENCE_BOTTLE, "<green><bold>EVOLUIR TIER",
                                "<gray>Próximo: <white>" + cm().nextTierMembers(clan) + " vagas",
                                "<gray>Custo: <gold>" + plugin.getEconomyManager().format(cost),
                                lore2, "", "<green>▶ Clique para evoluir (paga do cofre)"),
                        e -> {
                            cm().upgradeTier(viewer);
                            refresh(viewer);
                        });
            } else {
                setItem(31, ClanGui.item(plugin, Material.EXPERIENCE_BOTTLE, "<gray>Próximo tier",
                        "<gray>Próximo: <white>" + cm().nextTierMembers(clan) + " vagas",
                        "<gray>Custo: <gold>" + plugin.getEconomyManager().format(cost),
                        "<dark_gray>Só o líder pode evoluir."));
            }
        }

        setItem(40, ClanGui.item(plugin, Material.ARROW, "<yellow>Voltar"),
                e -> new ClanMenu(plugin).open(viewer));
        fillEmpty(ClanGui.filler());
    }
}
