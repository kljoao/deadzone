package com.deadzone.modules.claim;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Menu de evolução da base: altura, baús e (futuro) renovação. */
public class EvoluirMenu extends Menu {

    private final DeadzonePlugin plugin;
    private final ClaimManager manager;
    private final Claim claim;

    public EvoluirMenu(DeadzonePlugin plugin, ClaimManager manager, Claim claim) {
        this.plugin = plugin;
        this.manager = manager;
        this.claim = claim;
    }

    @Override
    public Component title() {
        return Component.text("Evoluir Base", NamedTextColor.DARK_GREEN);
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
        boolean hMax = claim.heightUpgrades() >= manager.heightMaxUpgrades();
        ItemStack height = ClaimIcons.item(Material.LADDER,
                Component.text("Subir altura", NamedTextColor.AQUA),
                Component.text("Nível: " + claim.heightUpgrades() + "/" + manager.heightMaxUpgrades(), NamedTextColor.GRAY),
                Component.text("Cada nível: +" + manager.heightBlocks() + " blocos", NamedTextColor.GRAY),
                Component.empty(),
                hMax ? Component.text("No máximo", NamedTextColor.DARK_GRAY)
                        : Component.text("Custo: " + manager.heightCostXp() + " XP", NamedTextColor.YELLOW));
        if (hMax) {
            setItem(10, height);
        } else {
            setItem(10, height, e -> {
                if (manager.upgradeHeight(viewer, claim)) {
                    refresh(viewer);
                }
            });
        }

        boolean cMax = claim.chestUpgrades() >= manager.chestMaxUpgrades();
        ItemStack buy = ClaimIcons.item(Material.CHEST,
                Component.text("Aumentar limite de baús", NamedTextColor.GOLD),
                Component.text("Limite atual: " + manager.chestLimit(claim) + " baús", NamedTextColor.GRAY),
                Component.text("Nível: " + claim.chestUpgrades() + "/" + manager.chestMaxUpgrades(), NamedTextColor.GRAY),
                Component.empty(),
                cMax ? Component.text("No máximo", NamedTextColor.DARK_GRAY)
                        : Component.text("Custo: " + manager.chestCostXp() + " XP (entrega os baús)",
                        NamedTextColor.YELLOW));
        if (cMax) {
            setItem(13, buy);
        } else {
            setItem(13, buy, e -> {
                if (manager.upgradeChests(viewer, claim)) {
                    refresh(viewer);
                }
            });
        }

        setItem(16, ClaimIcons.item(Material.EMERALD_BLOCK,
                        Component.text("Renovar base", NamedTextColor.GREEN),
                        Component.text("Disponível com a economia (em breve).", NamedTextColor.DARK_GRAY)),
                e -> viewer.sendActionBar(Component.text("Renovação chega com a economia.", NamedTextColor.GRAY)));

        setItem(22, ClaimIcons.item(Material.ARROW, Component.text("Voltar", NamedTextColor.GRAY)),
                e -> new BaseMenu(plugin, manager, claim).open(viewer));
        fillEmpty(ClaimIcons.filler());
    }
}
