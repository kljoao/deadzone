package com.deadzone.modules.clan.gui;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import com.deadzone.modules.clan.Clan;
import com.deadzone.modules.clan.ClanManager;
import com.deadzone.modules.clan.ClanRole;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Cofre do clã: depósitos (todos) e saques (Oficial+), com presets e valor custom. */
public class ClanBankMenu extends Menu {

    private static final long[] PRESETS = {100, 1000, 10000};

    private final DeadzonePlugin plugin;

    public ClanBankMenu(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    private ClanManager cm() {
        return plugin.getClanManager();
    }

    @Override
    public Component title() {
        return Component.text("Cofre do Clã", NamedTextColor.GOLD);
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
        ClanRole role = clan.roleOf(viewer.getUniqueId());
        boolean canWithdraw = role != null && role.canManageMembers();

        setItem(4, ClanGui.item(plugin, Material.GOLD_BLOCK, "<gold>Cofre",
                "<gray>Saldo: <gold>" + plugin.getEconomyManager().format(clan.bank()),
                "<gray>Seu saldo: <gold>" + plugin.getEconomyManager().format(plugin.getEconomyManager().balanceOf(viewer))));

        // Depósitos
        int[] depSlots = {10, 11, 12};
        for (int i = 0; i < PRESETS.length; i++) {
            long amt = PRESETS[i];
            setItem(depSlots[i], ClanGui.item(plugin, Material.GOLD_NUGGET,
                            "<green>Depositar " + plugin.getEconomyManager().format(amt)),
                    e -> {
                        cm().bankDeposit(viewer, amt);
                        refresh(viewer);
                    });
        }
        setItem(13, ClanGui.item(plugin, Material.GOLD_INGOT, "<green>Depositar tudo"),
                e -> {
                    cm().bankDeposit(viewer, plugin.getEconomyManager().balanceOf(viewer));
                    refresh(viewer);
                });
        setItem(14, ClanGui.item(plugin, Material.PAPER, "<green>Valor personalizado (depositar)"),
                e -> cm().prompts().await(viewer, "<yellow>Quanto deseja DEPOSITAR?", raw -> {
                    Long v = parse(viewer, raw);
                    if (v != null) {
                        cm().bankDeposit(viewer, v);
                    }
                    new ClanBankMenu(plugin).open(viewer);
                }));

        // Saques (Oficial+)
        if (canWithdraw) {
            int[] wdSlots = {19, 20, 21};
            for (int i = 0; i < PRESETS.length; i++) {
                long amt = PRESETS[i];
                setItem(wdSlots[i], ClanGui.item(plugin, Material.IRON_NUGGET,
                                "<red>Sacar " + plugin.getEconomyManager().format(amt)),
                        e -> {
                            cm().bankWithdraw(viewer, amt);
                            refresh(viewer);
                        });
            }
            setItem(22, ClanGui.item(plugin, Material.IRON_INGOT, "<red>Sacar tudo"),
                    e -> {
                        cm().bankWithdraw(viewer, clan.bank());
                        refresh(viewer);
                    });
            setItem(23, ClanGui.item(plugin, Material.PAPER, "<red>Valor personalizado (sacar)"),
                    e -> cm().prompts().await(viewer, "<yellow>Quanto deseja SACAR?", raw -> {
                        Long v = parse(viewer, raw);
                        if (v != null) {
                            cm().bankWithdraw(viewer, v);
                        }
                        new ClanBankMenu(plugin).open(viewer);
                    }));
        } else {
            setItem(19, ClanGui.item(plugin, Material.BARRIER, "<dark_gray>Saque: só Oficial+",
                    "<gray>Você não pode sacar do cofre."));
        }

        setItem(40, ClanGui.item(plugin, Material.ARROW, "<yellow>Voltar"),
                e -> new ClanMenu(plugin).open(viewer));
        fillEmpty(ClanGui.filler());
    }

    private Long parse(Player viewer, String raw) {
        try {
            long v = Long.parseLong(raw.trim());
            if (v <= 0) {
                viewer.sendMessage(Component.text("O valor precisa ser positivo.", NamedTextColor.RED));
                return null;
            }
            return v;
        } catch (NumberFormatException e) {
            viewer.sendMessage(Component.text("Valor inválido: " + raw, NamedTextColor.RED));
            return null;
        }
    }
}
