package com.deadzone.modules.loot;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Busca estilo Escape From Tarkov: os itens do baú são revelados um a um (~1 por segundo) e o
 * jogador só consegue PEGAR um item depois que ele carrega 100% (o slot só ganha ação no fim).
 */
public class LootSearchMenu extends Menu {

    private static final int FRAMES_PER_ITEM = 4; // 4 frames * 5 ticks = ~1s por item
    private static final long PERIOD_TICKS = 5L;

    private final DeadzonePlugin plugin;
    private final LootManager manager;
    private final LootContainer container;
    private final LootSession session;
    private final int slots;

    private int frame;
    private int taskId = -1;
    private Player viewer;

    public LootSearchMenu(DeadzonePlugin plugin, LootManager manager, LootContainer container,
                          LootSession session, int startRevealed) {
        this.plugin = plugin;
        this.manager = manager;
        this.container = container;
        this.session = session;
        this.slots = session.contents.length;
        this.frame = startRevealed * FRAMES_PER_ITEM; // retoma de onde parou
    }

    @Override
    public Component title() {
        return Component.text("Vasculhando — " + typeLabel(), NamedTextColor.DARK_GREEN);
    }

    @Override
    public int size() {
        int n = Math.max(1, slots);
        int rows = (n - 1) / 9 + 1;
        return Math.min(54, rows * 9);
    }

    @Override
    protected void build(Player v) {
        this.viewer = v;
        if (taskId == -1) {
            taskId = plugin.getServer().getScheduler()
                    .runTaskTimer(plugin, this::tick, PERIOD_TICKS, PERIOD_TICKS).getTaskId();
        }
        int revealed = frame / FRAMES_PER_ITEM;
        for (int i = 0; i < slots; i++) {
            if (i < revealed) {
                ItemStack real = session.contents[i];
                if (real == null) {
                    setItem(i, taken()); // outro jogador pegou
                } else {
                    final int slot = i;
                    setItem(i, real.clone(), e -> take(v, slot)); // carregado: pode pegar
                }
            } else if (i == revealed) {
                setItem(i, loading(frame % FRAMES_PER_ITEM)); // carregando agora
            } else {
                setItem(i, hidden()); // ainda não vasculhado
            }
        }
    }

    private void tick() {
        if (viewer == null || !viewer.isOnline()) {
            cancel();
            return;
        }
        int revealed = frame / FRAMES_PER_ITEM;
        if (revealed >= slots) {
            cancel();
            return;
        }
        frame++;
        refresh(viewer);
    }

    private void take(Player p, int slot) {
        ItemStack item = manager.takeItem(container, slot);
        if (item == null) {
            p.sendActionBar(Component.text("Alguém pegou primeiro.", NamedTextColor.GRAY));
            refresh(p);
            return;
        }
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(item);
        // Inventário cheio: o excedente cai aos pés (sem perder item).
        leftover.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
        p.playSound(p, Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
        refresh(p);
    }

    @Override
    public void onClose(Player v) {
        cancel();
        manager.saveProgress(v, container, frame / FRAMES_PER_ITEM); // retoma depois
        super.onClose(v);
    }

    private void cancel() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    // ----- ícones -----

    private ItemStack loading(int subFrame) {
        int bars = 10;
        int filled = (int) Math.round((subFrame / (double) FRAMES_PER_ITEM) * bars);
        String bar = "█".repeat(filled) + "░".repeat(bars - filled);
        ItemStack item = new ItemStack(Material.CLOCK);
        item.editMeta(meta -> meta.displayName(Component.text("Vasculhando... ", NamedTextColor.YELLOW)
                .append(Component.text(bar, NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false)));
        return item;
    }

    private ItemStack hidden() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.displayName(Component.text("???", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        return item;
    }

    private ItemStack taken() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.displayName(Component.text("(vazio)", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        return item;
    }

    private String typeLabel() {
        String t = container.type();
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
