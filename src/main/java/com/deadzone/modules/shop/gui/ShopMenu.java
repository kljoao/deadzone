package com.deadzone.modules.shop.gui;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import com.deadzone.modules.shop.ShopsConfig.Shop;
import com.deadzone.modules.shop.ShopsConfig.ShopEntry;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Menu de loja (compra ou venda) com layout emoldurado e temático. */
public class ShopMenu extends Menu {

    private static final String SEP = "<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬";

    private final DeadzonePlugin plugin;
    private final String shopKey;

    public ShopMenu(DeadzonePlugin plugin, String shopKey) {
        this.plugin = plugin;
        this.shopKey = shopKey;
    }

    private Shop shop() {
        return plugin.getShopManager().config().shop(shopKey);
    }

    @Override
    public Component title() {
        Shop shop = shop();
        return shop != null ? plugin.getMessages().parse(shop.title()) : Component.text("Loja");
    }

    @Override
    public int size() {
        return 54;
    }

    @Override
    protected void build(Player viewer) {
        Shop shop = shop();
        if (shop == null) {
            setItem(22, ShopGui.named(plugin, Material.BARRIER, "<red>Loja indisponível"));
            fillEmpty(ShopGui.pane(Material.BLACK_STAINED_GLASS_PANE));
            return;
        }

        // cabeçalho
        setItem(4, header(shop));

        // itens no miolo
        int idx = 0;
        for (ShopEntry e : shop.items()) {
            if (idx >= ShopGui.CONTENT.length) {
                break;
            }
            ItemStack disp = display(viewer, shop, e);
            if (disp == null) {
                continue; // item não registrado (ex.: munição inexistente) — pula
            }
            setItem(ShopGui.CONTENT[idx++], disp, ev -> {
                if (shop.sell()) {
                    plugin.getShopManager().sell(viewer, e, ev.isShiftClick());
                } else {
                    plugin.getShopManager().buy(viewer, e);
                }
                refresh(viewer);
            });
        }

        // rodapé: saldo + voltar (armeiro) + fechar
        setItem(49, ShopGui.named(plugin, Material.SUNFLOWER, "<gold><bold>Seu saldo",
                "<yellow>" + plugin.getEconomyManager().format(plugin.getEconomyManager().balanceOf(viewer))));
        if (shopKey.startsWith("armeiro_")) {
            setItem(45, ShopGui.named(plugin, Material.ARROW, "<yellow>Voltar", "<gray>Ao seletor do armeiro."),
                    ev -> new ArmeiroMenu(plugin).open(viewer));
        }
        setItem(53, ShopGui.named(plugin, Material.BARRIER, "<red>Fechar"),
                ev -> viewer.closeInventory());

        fillEmpty(ShopGui.pane(borderPane()));
    }

    private ItemStack header(Shop shop) {
        String desc = shop.sell()
                ? "<gray>Venda itens e ganhe scraps."
                : "<gray>Compre itens com seus scraps.";
        return ShopGui.named(plugin, headerIcon(), shop.title(),
                SEP, desc,
                shop.sell()
                        ? "<gray>Clique: <white>vender 1  <dark_gray>|  <gray>Shift: <white>vender tudo"
                        : "<gray>Clique num item para <white>comprar<gray>.");
    }

    /** Item de exibição: o próprio item + lore de preço/afford. */
    private ItemStack display(Player viewer, Shop shop, ShopEntry e) {
        ItemStack base = plugin.getShopManager().resolve(e, e.amount());
        if (base == null) {
            return null;
        }
        if (shop.sell()) {
            int have = plugin.getShopManager().countMatching(viewer, e);
            ShopGui.decorate(plugin, base, null,
                    "", SEP,
                    "<gold>⛁ Vende por <yellow>" + plugin.getEconomyManager().format(e.price()) + "<gray>/un",
                    "<gray>No inventário: <white>" + have,
                    have > 0 ? "<green>▶ Clique: vender 1  <dark_gray>|  Shift: tudo"
                            : "<red>✘ Você não tem este item");
        } else {
            long bal = plugin.getEconomyManager().balanceOf(viewer);
            boolean afford = bal >= e.price();
            ShopGui.decorate(plugin, base, null,
                    "", SEP,
                    "<gold>⛁ Preço: <yellow>" + plugin.getEconomyManager().format(e.price())
                            + (e.amount() > 1 ? "  <gray>(recebe x" + e.amount() + ")" : ""),
                    afford ? "<green>▶ Clique para comprar" : "<red>✘ Saldo insuficiente");
        }
        return base;
    }

    private Material borderPane() {
        return switch (shopKey) {
            case "medico" -> Material.RED_STAINED_GLASS_PANE;
            case "comprador" -> Material.ORANGE_STAINED_GLASS_PANE;
            default -> Material.GRAY_STAINED_GLASS_PANE; // armeiro_*
        };
    }

    private Material headerIcon() {
        return switch (shopKey) {
            case "medico" -> Material.GOLDEN_APPLE;
            case "comprador" -> Material.GOLD_INGOT;
            case "armeiro_mods" -> Material.AMETHYST_SHARD;
            default -> Material.IRON_INGOT; // armeiro_armas
        };
    }
}
