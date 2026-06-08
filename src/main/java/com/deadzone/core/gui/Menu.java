package com.deadzone.core.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base de menus baseados em inventário. Subclasses definem título, tamanho e conteúdo.
 * Cliques são sempre cancelados pelo MenuListener; ações por slot ficam aqui.
 */
public abstract class Menu {

    private final MenuHolder holder;
    private Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu() {
        this.holder = new MenuHolder(this);
    }

    public abstract Component title();

    /** Tamanho do inventário (múltiplo de 9). */
    public abstract int size();

    /** Popula os slots. Chamado em {@link #open(Player)} e em {@link #refresh(Player)}. */
    protected abstract void build(Player viewer);

    public void open(Player player) {
        this.inventory = Bukkit.createInventory(holder, size(), title());
        holder.setInventory(inventory);
        actions.clear();
        build(player);
        player.openInventory(inventory);
    }

    /** Reconstrói o conteúdo sem reabrir (evita flicker). */
    public void refresh(Player viewer) {
        if (inventory == null) {
            open(viewer);
            return;
        }
        inventory.clear();
        actions.clear();
        build(viewer);
    }

    protected void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    protected void setItem(int slot, ItemStack item) {
        setItem(slot, item, null);
    }

    /** Preenche todos os slots vazios com um item de "moldura" (ex.: vidro cinza). */
    protected void fillEmpty(ItemStack filler) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    /** Despacha o clique para a ação registrada no slot, se houver. */
    public void onClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    public Inventory getInventory() {
        return inventory;
    }
}
