package com.deadzone.modules.apocalypse;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.entity.EntityKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quando um jogador morre, o loot dele é guardado dentro de um zumbi com a sua cabeça/skin
 * e o seu nome; matar esse zumbi devolve o loot.
 */
public class PlayerZombieListener implements Listener {

    private final DeadzonePlugin plugin;
    private final Map<UUID, ItemStack[]> loot = new HashMap<>();

    public PlayerZombieListener(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Captura o inventário inteiro direto (independe de keepInventory).
        PlayerInventory inv = player.getInventory();
        List<ItemStack> all = new ArrayList<>();
        Collections.addAll(all, inv.getStorageContents());
        Collections.addAll(all, inv.getArmorContents());
        all.add(inv.getItemInOffHand());
        ItemStack[] loots = all.toArray(new ItemStack[0]);

        // Nada cai no chão nem fica com o jogador: tudo vai para o zumbi.
        event.getDrops().clear();
        inv.clear();

        byte[] data = ItemStack.serializeItemsAsBytes(loots);
        ItemStack head = playerHead(player);

        Zombie zombie = player.getWorld().spawn(player.getLocation(), Zombie.class, z -> {
            z.getPersistentDataContainer().set(EntityKeys.PLAYER_ZOMBIE_LOOT, PersistentDataType.BYTE_ARRAY, data);
            z.customName(Component.text(player.getName(), NamedTextColor.DARK_RED));
            z.setCustomNameVisible(true);
            z.setPersistent(true);
            z.setRemoveWhenFarAway(false);
            z.setCanPickupItems(false);
            if (z.getEquipment() != null) {
                z.getEquipment().setHelmet(head);
                z.getEquipment().setHelmetDropChance(0f);
            }
        });
        loot.put(zombie.getUniqueId(), loots);
    }

    @EventHandler
    public void onZombieDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        ItemStack[] items = loot.remove(entity.getUniqueId());
        if (items == null) {
            byte[] data = entity.getPersistentDataContainer()
                    .get(EntityKeys.PLAYER_ZOMBIE_LOOT, PersistentDataType.BYTE_ARRAY);
            if (data == null) {
                return;
            }
            items = ItemStack.deserializeItemsFromBytes(data);
        }
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                entity.getWorld().dropItemNaturally(entity.getLocation(), item);
            }
        }
    }

    private ItemStack playerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }
        return head;
    }
}
