package com.deadzone.modules.shop;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.item.CustomItem;
import com.deadzone.modules.shop.ShopsConfig.ShopEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Lógica de compra/venda das lojas. */
public class ShopManager {

    private final DeadzonePlugin plugin;
    private final ShopsConfig config;

    public ShopManager(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.config = new ShopsConfig(plugin, configManager);
    }

    public ShopsConfig config() {
        return config;
    }

    public void reload() {
        config.load();
    }

    /** Constrói o ItemStack de uma entrada (item Deadzone ou material), ou null se inválida. */
    public ItemStack resolve(ShopEntry e, int amount) {
        if (e.itemId() != null) {
            CustomItem ci = plugin.getItemRegistry().get(e.itemId());
            if (ci == null) {
                return null;
            }
            ItemStack s = ci.build();
            s.setAmount(Math.max(1, Math.min(amount, s.getMaxStackSize())));
            return s;
        }
        if (e.material() != null) {
            return new ItemStack(e.material(), Math.max(1, amount));
        }
        return null;
    }

    /** Compra: paga 'price' e recebe 'amount' itens. */
    public void buy(Player player, ShopEntry e) {
        ItemStack stack = resolve(e, e.amount());
        if (stack == null) {
            error(player, "Item indisponível.");
            return;
        }
        if (!plugin.getEconomyManager().tryDebit(player, e.price())) {
            error(player, "Saldo insuficiente — custa " + plugin.getEconomyManager().format(e.price())
                    + " (você tem " + plugin.getEconomyManager().format(plugin.getEconomyManager().balanceOf(player)) + ").");
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
        player.playSound(player, Sound.ENTITY_VILLAGER_YES, 0.7f, 1.2f);
        player.sendMessage(Component.text("Comprado " + e.amount() + "x por "
                + plugin.getEconomyManager().format(e.price()) + ".", NamedTextColor.GREEN));
    }

    /** Venda: vende 1 (all=false) ou todos os itens correspondentes do inventário. */
    public void sell(Player player, ShopEntry e, boolean all) {
        int available = countMatching(player, e);
        if (available <= 0) {
            error(player, "Você não tem esse item para vender.");
            return;
        }
        int qty = all ? available : 1;
        removeMatching(player, e, qty);
        long total = e.price() * qty;
        plugin.getEconomyManager().reward(player, total);
        player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.1f);
        player.sendMessage(Component.text("Vendido " + qty + "x por "
                + plugin.getEconomyManager().format(total) + ".", NamedTextColor.GOLD));
    }

    public int countMatching(Player player, ShopEntry e) {
        int total = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (matches(e, s)) {
                total += s.getAmount();
            }
        }
        return total;
    }

    private void removeMatching(Player player, ShopEntry e, int count) {
        int remaining = count;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack s = contents[i];
            if (!matches(e, s)) {
                continue;
            }
            int take = Math.min(remaining, s.getAmount());
            s.setAmount(s.getAmount() - take);
            remaining -= take;
            player.getInventory().setItem(i, s.getAmount() > 0 ? s : null);
        }
    }

    private boolean matches(ShopEntry e, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (e.itemId() != null) {
            return plugin.getItemRegistry().resolve(stack)
                    .map(ci -> ci.id().equalsIgnoreCase(e.itemId())).orElse(false);
        }
        if (e.material() != null) {
            // material vanilla puro (não um item Deadzone que usa esse material como base)
            return stack.getType() == e.material() && plugin.getItemRegistry().resolve(stack).isEmpty();
        }
        return false;
    }

    private void error(Player player, String msg) {
        player.sendMessage(Component.text(msg, NamedTextColor.RED));
    }
}
