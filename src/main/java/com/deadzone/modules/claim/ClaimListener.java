package com.deadzone.modules.claim;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;

/** Proteção da base: construir, baús/portas, núcleo, zona segura e respawn. */
public class ClaimListener implements Listener {

    private final DeadzonePlugin plugin;
    private final ClaimManager manager;

    public ClaimListener(DeadzonePlugin plugin, ClaimManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!manager.restrictPlace() || bypass(event.getPlayer())) {
            return;
        }
        if (!manager.canBuildAt(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (bypass(event.getPlayer())) {
            return;
        }
        Block block = event.getBlock();
        // O núcleo só o dono quebra.
        Claim nucleo = manager.claimByNucleo(block.getLocation());
        if (nucleo != null && !nucleo.owner().equals(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("Núcleo de outro jogador.", NamedTextColor.RED));
            return;
        }
        if (!manager.restrictBreak()) {
            return;
        }
        if (!manager.canBuildAt(event.getPlayer(), block.getLocation())) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    /** Núcleo (menu), e proteção de baús/portas na base de outros. */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        Claim nucleo = manager.claimByNucleo(block.getLocation());
        if (nucleo != null) {
            event.setCancelled(true);
            if (nucleo.owner().equals(player.getUniqueId())) {
                new BaseMenu(plugin, manager, nucleo).open(player);
            } else {
                player.sendActionBar(Component.text("Núcleo de outro jogador.", NamedTextColor.RED));
            }
            return;
        }

        if (bypass(player) || manager.claimAt(block.getLocation()) == null) {
            return;
        }
        Material m = block.getType();
        boolean container = block.getState() instanceof InventoryHolder;
        boolean door = Tag.DOORS.isTagged(m) || Tag.TRAPDOORS.isTagged(m) || Tag.FENCE_GATES.isTagged(m);
        if ((container && !manager.canContainers(player, block.getLocation()))
                || (door && !manager.canDoors(player, block.getLocation()))) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Sem permissão nesta base.", NamedTextColor.RED));
        }
    }

    /** Zona segura: zumbis não spawnam naturalmente dentro de uma base. */
    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.COMMAND
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }
        if (plugin.getInfectionManager().isZombieType(event.getEntity())
                && manager.claimAt(event.getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    /** Respawn na própria base (no núcleo). */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Claim claim = manager.getClaim(event.getPlayer().getUniqueId());
        if (claim == null) {
            return;
        }
        World world = Bukkit.getWorld(claim.world());
        if (world != null) {
            event.setRespawnLocation(new Location(world,
                    claim.nucleoX() + 0.5, claim.nucleoY() + 1, claim.nucleoZ() + 0.5));
        }
    }

    private boolean bypass(Player player) {
        return player.getGameMode() == GameMode.CREATIVE
                || player.hasPermission("deadzone.claim.bypass");
    }

    private void deny(Player player) {
        if (manager.hasClaim(player.getUniqueId())) {
            player.sendActionBar(Component.text("Você só pode construir dentro da sua base.", NamedTextColor.RED));
        } else {
            player.sendActionBar(Component.text("Reivindique uma base primeiro (Livro de Base).", NamedTextColor.RED));
        }
    }
}
