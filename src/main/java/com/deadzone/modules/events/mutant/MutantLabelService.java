package com.deadzone.modules.events.mutant;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mostra um rótulo (status do mutante) acima da cabeça do zumbi, mas SOMENTE para o
 * jogador que está perto e olhando para ele, e nunca através de paredes.
 *
 * Implementação: um TextDisplay invisível por padrão é criado quando o jogador mira no
 * mutante (com linha de visão), revelado só para ele, e removido quando desvia o olhar.
 */
public class MutantLabelService implements Listener {

    private static final int RANGE = 6;

    private final DeadzonePlugin plugin;
    private final MutantManager mutants;
    private final Map<UUID, Shown> shown = new HashMap<>();

    private int taskId = -1;

    public MutantLabelService(DeadzonePlugin plugin, MutantManager mutants) {
        this.plugin = plugin;
        this.mutants = mutants;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.taskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, 4L, 4L).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        shown.values().forEach(this::removeDisplay);
        shown.clear();
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            LivingEntity target = lookedAtMutant(player);
            Shown current = shown.get(player.getUniqueId());

            if (target == null) {
                if (current != null) {
                    removeDisplay(current);
                    shown.remove(player.getUniqueId());
                }
                continue;
            }

            if (current != null && current.zombieId.equals(target.getUniqueId()) && current.display.isValid()) {
                current.display.teleport(labelLocation(target)); // acompanha a cabeça
            } else {
                if (current != null) {
                    removeDisplay(current);
                }
                TextDisplay display = createLabel(player, target);
                shown.put(player.getUniqueId(), new Shown(display, target.getUniqueId()));
            }
        }
    }

    private LivingEntity lookedAtMutant(Player player) {
        Entity target = player.getTargetEntity(RANGE);
        if (target instanceof LivingEntity living
                && mutants.getType(living) != null
                && player.hasLineOfSight(living)) {
            return living;
        }
        return null;
    }

    private TextDisplay createLabel(Player viewer, LivingEntity zombie) {
        MutantType type = mutants.getType(zombie);
        Component text = labelText(type);
        TextDisplay display = zombie.getWorld().spawn(labelLocation(zombie), TextDisplay.class, td -> {
            td.text(text);
            td.setBillboard(Display.Billboard.CENTER);
            td.setSeeThrough(false);     // não atravessa blocos
            td.setViewRange(0.6f);       // só visível de perto
            td.setPersistent(false);     // não salva no mundo
            td.setVisibleByDefault(false); // ninguém vê por padrão
        });
        viewer.showEntity(plugin, display); // só o observador vê
        return display;
    }

    private Location labelLocation(LivingEntity zombie) {
        return zombie.getLocation().add(0, zombie.getHeight() + 0.4, 0);
    }

    private void removeDisplay(Shown shownLabel) {
        if (shownLabel.display != null && shownLabel.display.isValid()) {
            shownLabel.display.remove();
        }
    }

    private Component labelText(MutantType type) {
        if (type == null) {
            return Component.empty();
        }
        return switch (type) {
            case RUNNER -> Component.text("⚡ Corredor", NamedTextColor.YELLOW);
            case TANK -> Component.text("🛡 Tanque", NamedTextColor.GRAY);
            case EXPLODER -> Component.text("☣ Explosivo", NamedTextColor.GREEN);
        };
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Shown current = shown.remove(event.getPlayer().getUniqueId());
        if (current != null) {
            removeDisplay(current);
        }
    }

    private record Shown(TextDisplay display, UUID zombieId) {
    }
}
