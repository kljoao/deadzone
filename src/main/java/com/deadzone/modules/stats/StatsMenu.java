package com.deadzone.modules.stats;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import com.deadzone.core.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Menu de estatísticas de sobrevivência (/stats). Funciona para si ou para outro jogador online. */
public class StatsMenu extends Menu {

    private final DeadzonePlugin plugin;
    private final UUID targetUuid;
    private final String targetName;
    private final PlayerProfile profile;

    public StatsMenu(DeadzonePlugin plugin, UUID targetUuid, String targetName, PlayerProfile profile) {
        this.plugin = plugin;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.profile = profile;
    }

    @Override
    public Component title() {
        return Component.text("Estatísticas — " + targetName, NamedTextColor.DARK_AQUA);
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    protected void build(Player viewer) {
        setItem(4, head());

        setItem(10, stat(Material.ROTTEN_FLESH, "<green>Zumbis abatidos",
                String.valueOf(profile.getZombiesKilled())));
        setItem(11, stat(Material.IRON_SWORD, "<red>Jogadores abatidos",
                String.valueOf(profile.getPlayersKilled())));
        setItem(12, stat(Material.WITHER_SKELETON_SKULL, "<dark_gray>Mortes",
                String.valueOf(profile.getDeaths())));
        setItem(13, stat(Material.DIAMOND, "<aqua>K/D <gray>(abates de jogador ÷ mortes)", kd()));
        setItem(14, stat(Material.TOTEM_OF_UNDYING, "<gold>Reanimações feitas",
                String.valueOf(profile.getRevives())));
        setItem(15, stat(Material.CLOCK, "<yellow>Maior sobrevivência",
                profile.getBestSurvivalMs() > 0 ? formatDuration(profile.getBestSurvivalMs()) : "—"));
        setItem(16, stat(Material.COMPASS, "<white>Vida atual (sobrevivendo há)",
                formatDuration(Math.max(0, System.currentTimeMillis() - profile.getLifeStartedAt()))));
        setItem(22, stat(Material.IRON_NUGGET, "<gold>Scraps",
                plugin.getEconomyManager().format(profile.getBalance())));

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.empty()));
        fillEmpty(filler);
    }

    private ItemStack head() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(meta -> {
            if (meta instanceof SkullMeta skull) {
                skull.setOwningPlayer(Bukkit.getOfflinePlayer(targetUuid));
            }
            meta.displayName(plugin.getMessages().parse("<aqua><bold>" + targetName)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    plugin.getMessages().parse("<gray>Classe: <white>" + className())
                            .decoration(TextDecoration.ITALIC, false),
                    plugin.getMessages().parse("<gray>XP total: <white>" + profile.getTotalXpEarned())
                            .decoration(TextDecoration.ITALIC, false)));
        });
        return head;
    }

    private String className() {
        return switch (profile.getPlayerClass()) {
            case MEDICO -> "Médico";
            case BRUTO -> "Bruto";
            case SAQUEADOR -> "Saqueador";
            case NONE -> "Sem classe";
        };
    }

    private String kd() {
        long deaths = profile.getDeaths();
        long kills = profile.getPlayersKilled();
        double ratio = deaths == 0 ? kills : (double) kills / deaths;
        return String.format(Locale.US, "%.2f", ratio);
    }

    private ItemStack stat(Material material, String miniName, String value) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(plugin.getMessages().parse(miniName).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(plugin.getMessages().parse("<white>" + value)
                    .decoration(TextDecoration.ITALIC, false)));
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        });
        return item;
    }

    private String formatDuration(long ms) {
        long totalMin = ms / 60000L;
        long d = totalMin / (60 * 24);
        long h = (totalMin % (60 * 24)) / 60;
        long m = totalMin % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) {
            sb.append(d).append("d ");
        }
        if (d > 0 || h > 0) {
            sb.append(h).append("h ");
        }
        sb.append(m).append("min");
        return sb.toString();
    }
}
