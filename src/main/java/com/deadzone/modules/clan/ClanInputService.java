package com.deadzone.modules.clan;

import com.deadzone.DeadzonePlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Prompts de texto via chat para os menus de clan (criar, convidar, valor custom do cofre).
 * Quando há um prompt pendente, a próxima mensagem do jogador é capturada (não vai pro chat).
 */
public class ClanInputService implements Listener {

    private static final long TTL_MS = 60_000L; // prompt expira em 60s

    private final DeadzonePlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ClanInputService(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    /** Fecha o inventário e aguarda a próxima mensagem do jogador no chat (até 60s). */
    public void await(Player player, String promptMini, Consumer<String> onInput) {
        pending.put(player.getUniqueId(), new Pending(onInput, System.currentTimeMillis() + TTL_MS));
        player.closeInventory();
        player.sendMessage(plugin.getMessages().parse(promptMini));
        player.sendMessage(Component.text("Digite no chat (ou 'cancelar' para sair).", NamedTextColor.GRAY));
    }

    public void cancel(UUID uuid) {
        pending.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Pending p = pending.remove(id); // atômico: só uma thread leva o prompt (evita callback duplo)
        if (p == null) {
            return;
        }
        if (System.currentTimeMillis() > p.expiresAt()) {
            // expirou: deixa a mensagem seguir como chat normal
            return;
        }
        event.setCancelled(true); // não publica no chat
        String msg = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (msg.equalsIgnoreCase("cancelar")) {
                player.sendMessage(Component.text("Cancelado.", NamedTextColor.GRAY));
                return;
            }
            p.callback().accept(msg);
        });
    }

    private record Pending(Consumer<String> callback, long expiresAt) {
    }
}
