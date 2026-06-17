package com.deadzone.core.chat;

import com.deadzone.DeadzonePlugin;
import com.deadzone.modules.clan.Clan;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Formata o chat global: prefixo de rank do LuckPerms (via Vault) + tag colorida do clan,
 * resultando em "[VIP+] [TAG] Fulano: mensagem". ENVOLVE o renderer atual (compõe com o
 * ChatColor/EZColors em vez de substituir). Prioridade HIGH para prefixar o resultado final.
 */
public class ChatFormatListener implements Listener {

    private final DeadzonePlugin plugin;
    private Chat vaultChat; // resolvido sob demanda (LuckPerms pode registrar depois do nosso enable)

    public ChatFormatListener(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        Component prefix = Component.empty();
        boolean has = false;

        Component rank = rankPrefix(player);
        if (rank != null) {
            prefix = prefix.append(rank);
            has = true;
        }

        Clan clan = plugin.getClanManager().getClanOf(player.getUniqueId());
        if (clan != null) {
            prefix = prefix.append(Component.text("[" + clan.tag() + "] ", clan.namedColor()));
            has = true;
        }

        if (!has) {
            return;
        }
        Component finalPrefix = prefix;
        ChatRenderer previous = event.renderer();
        event.renderer((source, sourceDisplayName, message, viewer) ->
                finalPrefix.append(previous.render(source, sourceDisplayName, message, viewer)));
    }

    /** Prefixo do LuckPerms via Vault (com códigos de cor legados), ou null se vazio/indisponível. */
    private Component rankPrefix(Player player) {
        Chat chat = vaultChat();
        if (chat == null) {
            return null;
        }
        String raw;
        try {
            raw = chat.getPlayerPrefix(player);
        } catch (Throwable t) {
            return null;
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // Normaliza § -> & e desserializa (suporta cores legadas e hex).
        Component c = LegacyComponentSerializer.legacyAmpersand().deserialize(raw.replace('§', '&'));
        return c.append(Component.space());
    }

    private Chat vaultChat() {
        if (vaultChat == null) {
            try {
                RegisteredServiceProvider<Chat> rsp =
                        plugin.getServer().getServicesManager().getRegistration(Chat.class);
                if (rsp != null) {
                    vaultChat = rsp.getProvider();
                }
            } catch (Throwable ignored) {
                // Vault/chat ausente: segue só com a tag do clan
            }
        }
        return vaultChat;
    }
}
