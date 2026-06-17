package com.deadzone.core.resourcepack;

import com.deadzone.DeadzonePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Envia o resource pack ao jogador no login, se configurado em config.yml. */
public class ResourcePackListener implements Listener {

    private final DeadzonePlugin plugin;

    public ResourcePackListener(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        FileConfiguration c = plugin.getConfigManager().raw();
        if (!c.getBoolean("resource-pack.enabled", false)) {
            return;
        }
        String url = c.getString("resource-pack.url", "");
        if (url == null || url.isBlank()) {
            return;
        }
        String sha1 = c.getString("resource-pack.sha1", "");
        if (sha1 == null || sha1.isBlank()) {
            plugin.getLogger().warning("resource-pack.sha1 vazio: o cliente rebaixa o pack a cada login "
                    + "(sem cache/validação). Preencha o SHA-1 do .zip em config.yml.");
        }
        boolean force = c.getBoolean("resource-pack.force", false);
        var prompt = plugin.getMessages().parse(c.getString("resource-pack.prompt", ""));
        event.getPlayer().setResourcePack(url, sha1, force, prompt);
    }
}
