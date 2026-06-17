package com.deadzone.modules.claim;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.MenuHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
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
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
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
        locks.markPending(block); // trava a abertura até o dono definir o PIN (sem janela aberta)
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
                    locks.clearPending(block);
                    player.playSound(player, Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 1.3f);
                    player.sendActionBar(Component.text("Baú trancado!", NamedTextColor.GREEN));
                    return true;
                },
                () -> {
                    locks.clearPending(block);
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
        if (block == null || !LockedChestManager.isChest(block.getType())) {
            return;
        }
        Player player = event.getPlayer();
        if (locks.isPending(block)) {
            event.setCancelled(true); // baú recém-colocado, ainda sem PIN: ninguém abre
            player.sendActionBar(Component.text("Baú sendo trancado pelo dono...", NamedTextColor.GRAY));
            return;
        }
        if (!locks.isLocked(block)) {
            return;
        }
        event.setCancelled(true); // a abertura é controlada aqui
        if (locks.canOpen(player, block)) {
            openChest(player, block);
        } else {
            player.sendMessage(Component.text("🔒 Baú trancado. Digite a senha de 4 dígitos para abrir.",
                    NamedTextColor.AQUA));
            new PinKeypadMenu(PinKeypadMenu.Mode.UNLOCK,
                    pin -> {
                        LockedChest lc = locks.getLock(block);
                        if (lc != null && lc.checkPin(pin)) {
                            locks.authorize(block, player.getUniqueId());
                            player.sendActionBar(Component.text("Baú desbloqueado!", NamedTextColor.GREEN));
                            plugin.getServer().getScheduler().runTask(plugin, () -> openChest(player, block));
                            return true;
                        }
                        punishWrongPin(player); // anti-brute-force: erra = leva dano
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

    /** Penalidade por errar o PIN: -3 corações (sem sangramento), choque visual, náusea e aviso. */
    private void punishWrongPin(Player player) {
        player.damage(6.0); // dano "custom": não é de zumbi/arma, então não dispara sangramento
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.4f);
        player.getWorld().spawnParticle(Particle.CRIT, player.getEyeLocation(), 12, 0.3, 0.3, 0.3, 0.1);
        PotionEffectType nausea = Registry.EFFECT.get(NamespacedKey.minecraft("nausea"));
        if (nausea != null) {
            player.addPotionEffect(new PotionEffect(nausea, 60, 0, false, true, true));
        }
        player.sendMessage(Component.text("⚡ Senha incorreta! A fechadura deu um choque (-3 ❤).",
                NamedTextColor.RED));
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

    /** Funis/automação NÃO movem itens para dentro nem para fora de baús trancados (bypass do PIN). */
    @EventHandler(ignoreCancelled = true)
    public void onItemMove(InventoryMoveItemEvent event) {
        if (isLockedInventory(event.getSource()) || isLockedInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    private boolean isLockedInventory(Inventory inv) {
        InventoryHolder holder = inv.getHolder();
        if (holder instanceof DoubleChest dc) {
            return isLockedHolder(dc.getLeftSide()) || isLockedHolder(dc.getRightSide());
        }
        return isLockedHolder(holder);
    }

    private boolean isLockedHolder(InventoryHolder holder) {
        return holder instanceof Chest chest && locks.isLocked(chest.getBlock());
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
