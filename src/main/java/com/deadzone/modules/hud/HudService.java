package com.deadzone.modules.hud;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.profile.BleedState;
import com.deadzone.core.profile.PlayerClass;
import com.deadzone.core.profile.PlayerProfile;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Painel lateral animado: título com brilho varrendo, barras de progresso,
 * divisórias com efeito de scanner e linha de alerta piscante.
 */
public class HudService implements Listener {

    private static final int LINES = 10;
    private static final String[] ENTRIES;

    static {
        String codes = "0123456789ab";
        ENTRIES = new String[codes.length()];
        for (int i = 0; i < codes.length(); i++) {
            ENTRIES[i] = "§" + codes.charAt(i);
        }
    }

    private final DeadzonePlugin plugin;
    private final HudConfig config;
    private final Map<UUID, Sidebar> sidebars = new HashMap<>();

    // Cache de parse (MiniMessage é caro). As mesmas strings se repetem entre players e frames,
    // então a taxa de acerto é altíssima — derruba ~3.300 parses/seg para algumas dezenas.
    // LRU limitado (só main thread); a string é a chave (mesma string => mesmo Component).
    private final Map<String, Component> parseCache = new LinkedHashMap<>(512, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Component> eldest) {
            return size() > 1024;
        }
    };

    private int taskId = -1;
    private int frame;

    private Component cachedParse(String raw) {
        return parseCache.computeIfAbsent(raw, plugin.getMessages()::parse);
    }

    public HudService(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.config = new HudConfig(configManager);
    }

    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startTask();
    }

    public void disable() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        clearAll();
    }

    public void reload() {
        config.load();
        clearAll();
        startTask();
    }

    private void startTask() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
        long interval = config.animationInterval();
        this.taskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::run, interval, interval).getTaskId();
    }

    private void run() {
        if (!config.enabled()) {
            clearAll();
            return;
        }
        frame++;
        Component title = titleComponent(); // depende só do frame: computa 1x p/ todos os players
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
            if (profile == null) {
                continue;
            }
            Sidebar sidebar = sidebars.computeIfAbsent(player.getUniqueId(), k -> build(player));
            update(player, sidebar, profile, title);
        }
    }

    private Sidebar build(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("dz", Criteria.DUMMY, titleComponent());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());

        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < LINES; i++) {
            Team team = board.registerNewTeam("dz" + i);
            team.addEntry(ENTRIES[i]);
            objective.getScore(ENTRIES[i]).setScore(LINES - i);
            teams.add(team);
        }
        player.setScoreboard(board);
        return new Sidebar(objective, teams);
    }

    private void update(Player player, Sidebar sidebar, PlayerProfile profile, Component title) {
        sidebar.objective().displayName(title);
        List<String> lines = computeLines(player, profile);
        for (int i = 0; i < LINES; i++) {
            sidebar.teams().get(i).prefix(cachedParse(lines.get(i)));
        }
    }

    private List<String> computeLines(Player player, PlayerProfile profile) {
        int len = config.barLength();
        List<String> lines = new ArrayList<>(LINES);

        lines.add(divider(frame));

        boolean infected = profile.isInfected();
        int infLevel = (int) profile.getInfectionLevel();
        String infColor = infected ? infectionColor(infLevel) : "<green>";
        lines.add(meter(infColor, "Infecção", infected ? infLevel : 0, len));

        double sanity = profile.getSanity();
        String sanColor = goodHigh(sanity);
        lines.add(meter(sanColor, "Sanidade", sanity, len));

        BleedState bleed = plugin.getMedicineManager().bleeding().getBleed(player.getUniqueId());
        boolean wound = plugin.getMedicineManager().bleeding().isWoundInfected(player.getUniqueId());
        if (bleed != null) {
            lines.add("<red>● <white>Sangramento: <red>nível " + bleed.getSeverity());
        } else if (wound) {
            lines.add("<gold>● <white>Ferida: <gold>infeccionada");
        } else {
            lines.add("<green>● <white>Sangramento: <green>estável");
        }

        lines.add(divider(frame + 8));

        lines.add("<gold>✦ <white>Classe: <gold>" + classLabel(profile.getPlayerClass()));
        lines.add("<yellow>★ <white>XP: <yellow>" + profile.getXp());

        long day = player.getWorld().getFullTime() / 24000L;
        String timeIcon = player.getWorld().isDayTime() ? "<gold>☀" : "<aqua>☾";
        lines.add(timeIcon + " <white>Dia <aqua>" + day);

        lines.add(divider(frame + 4));

        boolean critical = (infected && infLevel >= 75) || sanity < 25 || bleed != null;
        if (critical) {
            String color = (frame / 4) % 2 == 0 ? "<red><bold>" : "<dark_red>";
            lines.add(color + "⚠ PERIGO ⚠");
        } else {
            lines.add(serverAddress(player));
        }
        return lines;
    }

    private String serverAddress(Player player) {
        InetSocketAddress vhost = player.getVirtualHost();
        if (vhost == null) {
            return config.footer();
        }
        String host = vhost.getHostString();
        return "<dark_gray>" + (vhost.getPort() == 25565 ? host : host + ":" + vhost.getPort());
    }

    private Component titleComponent() {
        if (config.titleLogo()) {
            return Component.text(""); // glifo da logo (fonte do resource pack)
        }
        String text = config.title();
        double highlight = (frame * 0.5) % (text.length() + 6);
        Component core = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            double dist = Math.abs(i - highlight);
            double brightness = Math.max(0, 1.0 - dist / 2.0);
            core = core.append(Component.text(text.charAt(i))
                    .color(lerp(0x8B0000, 0xFFE0E0, brightness))
                    .decoration(TextDecoration.BOLD, true));
        }
        return Component.text("☣ ", NamedTextColor.DARK_RED)
                .append(core)
                .append(Component.text(" ☣", NamedTextColor.DARK_RED));
    }

    private String divider(int phase) {
        int width = 16;
        int pos = Math.floorMod(phase, width);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < width; i++) {
            if (i == pos) {
                sb.append("<red>━");
            } else if (Math.abs(i - pos) == 1) {
                sb.append("<#8b1a1a>━");
            } else {
                sb.append("<dark_gray>━");
            }
        }
        return sb.toString();
    }

    /** Linha de medidor: barra em posição fixa (após o ●) e rótulo/valor depois, para alinhar. */
    private String meter(String color, String label, double pct, int length) {
        pct = Math.max(0, Math.min(100, pct));
        int filled = Math.max(0, Math.min(length, (int) Math.round(pct / 100.0 * length)));
        return color + "● " + color + "█".repeat(filled) + "<dark_gray>" + "░".repeat(length - filled)
                + " <white>" + label + " " + color + Math.round(pct) + "%";
    }

    private String goodHigh(double pct) {
        return pct >= 66 ? "<green>" : pct >= 33 ? "<gold>" : "<red>";
    }

    private String infectionColor(int level) {
        return level >= 75 ? "<dark_red>" : level >= 50 ? "<red>" : level >= 25 ? "<gold>" : "<yellow>";
    }

    private String classLabel(PlayerClass clazz) {
        return switch (clazz) {
            case NONE -> "Nenhuma";
            case MEDICO -> "Médico";
            case BRUTO -> "Bruto";
            case SAQUEADOR -> "Saqueador";
        };
    }

    private TextColor lerp(int from, int to, double t) {
        t = Math.max(0, Math.min(1, t));
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        return TextColor.color(
                (int) (fr + (tr - fr) * t),
                (int) (fg + (tg - fg) * t),
                (int) (fb + (tb - fb) * t));
    }

    private void clearAll() {
        if (sidebars.isEmpty()) {
            return;
        }
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (UUID uuid : sidebars.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                player.setScoreboard(main);
            }
        }
        sidebars.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sidebars.remove(event.getPlayer().getUniqueId());
    }

    private record Sidebar(Objective objective, List<Team> teams) {
    }
}
