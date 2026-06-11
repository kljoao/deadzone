package com.deadzone.modules.claim;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.MenuHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Baús físicos trancados: colocar (define senha), abrir (PIN), quebrar (só dono) e proteção. */
public class LockedChestListener implements Listener {

    private final DeadzonePlugin plugin;
    private final ClaimManager claims;
    private final LockedChestManager locks;

    public LockedChestListener(DeadzonePlugin plugin, ClaimManager claims) {
        this.plugin = plugin;
        this.claims = claims;
        this.locks = claims.lockedChests();
    }

    /** Colocar um baú na base: define senha (ou herda, se for baú duplo do mesmo dono). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!LockedChestManager.isChest(block.getType())) {
            return;
        }
        Player player = event.getPlayer();
        Claim claim = claims.claimAt(block.getLocation());
        if (claim == null) {
            return;
        }
        LockedChest adjacent = locks.adjacentLockOfOwner(block, player.getUniqueId());
        int limit = claims.chestLimit(claim);
        if (locks.countInClaim(claim) >= limit) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Limite de baús da base atingido (" + limit + ").",
                    NamedTextColor.RED));
            return;
        }
        if (adjacent != null) {
            locks.inherit(block, adjacent); // baú duplo: mesma senha, sem teclado
            player.sendActionBar(Component.text("Baú duplo — mesma senha do baú vizinho.", NamedTextColor.GREEN));
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> openSetKeypad(player, block));
    }

    private void openSetKeypad(Player player, Block block) {
        if (!LockedChestManager.isChest(block.getType()) || locks.isLocked(block)) {
            return;
        }
        Material type = block.getType();
        player.sendMessage(Component.text("🔒 Defina a senha do baú: digite 4 dígitos e Confirmar, "
                + "depois digite de novo e Confirmar.", NamedTextColor.AQUA));
        new PinKeypadMenu(PinKeypadMenu.Mode.SET,
                pin -> {
                    locks.lockNew(block, player.getUniqueId(), pin);
                    player.playSound(player, Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 1.3f);
                    player.sendActionBar(Component.text("Baú trancado!", NamedTextColor.GREEN));
                    return true;
                },
                () -> {
                    if (LockedChestManager.isChest(block.getType()) && !locks.isLocked(block)) {
                        block.setType(Material.AIR, false);
                        player.getInventory().addItem(new ItemStack(type));
                        player.sendActionBar(Component.text("Baú cancelado e devolvido.", NamedTextColor.GRAY));
                    }
                }).open(player);
    }

    /** Abrir um baú trancado: dono/membros direto; estranho digita o PIN. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !LockedChestManager.isChest(block.getType()) || !locks.isLocked(block)) {
            return;
        }
        Player player = event.getPlayer();
        event.setCancelled(true); // a abertura é controlada aqui
        if (locks.canOpen(player, block)) {
            openChest(player, block);
        } else {
            player.sendMessage(Component.text("🔒 Baú trancado. Digite a senha de 4 dígitos para abrir.",
                    NamedTextColor.AQUA));
            new PinKeypadMenu(PinKeypadMenu.Mode.UNLOCK,
                    pin -> {
                        LockedChest lc = locks.getLock(block);
                        if (lc != null && lc.pin().equals(pin)) {
                            locks.authorize(block, player.getUniqueId());
                            player.sendActionBar(Component.text("Baú desbloqueado!", NamedTextColor.GREEN));
                            plugin.getServer().getScheduler().runTask(plugin, () -> openChest(player, block));
                            return true;
                        }
                        return false;
                    },
                    () -> {
                    }).open(player);
        }
    }

    private void openChest(Player player, Block block) {
        if (block.getState() instanceof Chest chest) {
            player.openInventory(chest.getInventory());
        }
    }

    /** Só o dono quebra o próprio baú trancado. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!locks.isLocked(block)) {
            return;
        }
        LockedChest lc = locks.getLock(block);
        if (!lc.owner().equals(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("Baú trancado de outro jogador.", NamedTextColor.RED));
            return;
        }
        locks.removeLock(block); // dono quebra: libera a tranca e 1 do limite
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(locks::isLocked);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(locks::isLocked);
    }

    /** Fechar o teclado sem concluir dispara o cancelamento (devolve o baú no modo SET). */
    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder mh
                && mh.getMenu() instanceof PinKeypadMenu keypad) {
            keypad.handleClose();
        }
    }
}
