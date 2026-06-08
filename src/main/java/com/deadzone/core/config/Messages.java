package com.deadzone.core.config;

import com.deadzone.DeadzonePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

/** Strings exibidas (MiniMessage) carregadas de messages.yml, com placeholders simples. */
public class Messages {

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private FileConfiguration messages;
    private Component prefix;

    public Messages(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    public void load() {
        this.messages = configManager.loadConfig("messages.yml");
        this.prefix = mm.deserialize(messages.getString("prefix", ""));
    }

    /** Componente já formatado de uma chave (sem prefixo). */
    public Component get(String key, TagResolver... resolvers) {
        String raw = messages.getString(key, key);
        return mm.deserialize(raw, resolvers);
    }

    /** Faz parse de uma string MiniMessage arbitrária. */
    public Component parse(String miniMessage, TagResolver... resolvers) {
        return mm.deserialize(miniMessage, resolvers);
    }

    /**
     * Envia uma mensagem (com prefixo) ao destinatário.
     * Placeholders vêm em pares: chave, valor, chave, valor...
     * Ex.: send(sender, "item-given", "item", "Bandagem");
     */
    public void send(CommandSender to, String key, String... placeholders) {
        TagResolver[] resolvers = toResolvers(placeholders);
        to.sendMessage(prefix.append(get(key, resolvers)));
    }

    /** Igual a {@link #send} porém sem o prefixo. */
    public void sendRaw(CommandSender to, String key, String... placeholders) {
        to.sendMessage(get(key, toResolvers(placeholders)));
    }

    public Component prefix() {
        return prefix;
    }

    private TagResolver[] toResolvers(String... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholders devem vir em pares chave/valor.");
        }
        TagResolver[] resolvers = new TagResolver[placeholders.length / 2];
        for (int i = 0; i < placeholders.length; i += 2) {
            resolvers[i / 2] = Placeholder.unparsed(placeholders[i], placeholders[i + 1]);
        }
        return resolvers;
    }
}
