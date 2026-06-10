package com.deadzone.modules.claim;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.item.CustomItem;
import com.deadzone.core.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Sistema de Bases: livro de claim, preview com partículas, criação e restrição de construção. */
public class ClaimManager {

    public static final String BOOK_ID = "livro_base";

    private static final Color GREEN = Color.fromRGB(60, 220, 60);
    private static final Color RED = Color.fromRGB(230, 40, 40);
    private static final Color GOLD = Color.fromRGB(255, 200, 40);

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;
    private final File dataFile;
    private final Map<UUID, Claim> claims = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Boolean> inBase = new HashMap<>();
    private BukkitTask previewTask;

    private boolean enabled;
    private int size;
    private int heightUp;
    private int heightDown;
    private int nearOffset;
    private int previewInterval;
    private boolean restrictPlace;
    private boolean restrictBreak;
    private int markerHeight;
    private int markerSeconds;
    private Material markerBlock;
    private Material nucleoBlock;

    public ClaimManager(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dataFile = new File(plugin.getDataFolder(), "claims.yml");
        loadConfig();
        loadClaims();
    }

    public void enable() {
        plugin.getItemRegistry().register(new LivroDeBase(plugin, this));
        plugin.getServer().getPluginManager().registerEvents(new ClaimListener(plugin, this), plugin);
        plugin.getTickService().registerSecondHandler(this::baseTick);
        previewTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::previewTick, previewInterval, previewInterval);
    }

    /** Título ao entrar/sair da própria base. */
    private void baseTick(PlayerProfile profile) {
        Player player = plugin.getServer().getPlayer(profile.getUuid());
        if (player == null) {
            return;
        }
        boolean now = isInOwnBase(player);
        Boolean was = inBase.put(profile.getUuid(), now);
        if (was == null || was == now) {
            return;
        }
        if (now) {
            player.showTitle(Title.title(Component.text("🏠 Sua base", NamedTextColor.GREEN), Component.empty(),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(400))));
        } else {
            player.showTitle(Title.title(Component.text("Saindo da base", NamedTextColor.GRAY), Component.empty(),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1000), Duration.ofMillis(400))));
        }
    }

    public boolean isInOwnBase(Player player) {
        Claim c = claimAt(player.getLocation());
        return c != null && c.owner().equals(player.getUniqueId());
    }

    public Claim claimByNucleo(org.bukkit.Location loc) {
        String world = loc.getWorld().getName();
        for (Claim c : claims.values()) {
            if (c.world().equals(world) && c.isNucleo(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                return c;
            }
        }
        return null;
    }

    public void disable() {
        if (previewTask != null) {
            previewTask.cancel();
        }
        saveClaims();
    }

    public void reload() {
        loadConfig();
    }

    public boolean restrictPlace() {
        return restrictPlace;
    }

    public boolean restrictBreak() {
        return restrictBreak;
    }

    // ----- consultas -----

    public boolean hasClaim(UUID uuid) {
        return claims.containsKey(uuid);
    }

    public Claim getClaim(UUID uuid) {
        return claims.get(uuid);
    }

    /** Claim que contém um ponto, ou null. */
    public Claim claimAt(Location loc) {
        String world = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        for (Claim c : claims.values()) {
            if (c.world().equals(world) && c.contains(x, y, z)) {
                return c;
            }
        }
        return null;
    }

    /** Construir: dono OU membro com a permissão de quebrar/colocar. */
    public boolean canBuildAt(Player player, Location loc) {
        Claim c = claimAt(loc);
        if (c == null) {
            return false;
        }
        return c.owner().equals(player.getUniqueId()) || c.hasFlag(player.getUniqueId(), Claim.FLAG_BUILD);
    }

    public boolean canContainers(Player player, Location loc) {
        return canFlag(player, loc, Claim.FLAG_CONTAINERS);
    }

    public boolean canDoors(Player player, Location loc) {
        return canFlag(player, loc, Claim.FLAG_DOORS);
    }

    private boolean canFlag(Player player, Location loc, String flag) {
        Claim c = claimAt(loc);
        if (c == null) {
            return true; // fora de qualquer base: liberado
        }
        return c.owner().equals(player.getUniqueId()) || c.hasFlag(player.getUniqueId(), flag);
    }

    public void addMember(Claim claim, UUID member) {
        claim.addMember(member);
        saveClaims();
    }

    public void removeMember(Claim claim, UUID member) {
        claim.removeMember(member);
        saveClaims();
    }

    public void toggleFlag(Claim claim, UUID member, String flag) {
        claim.setFlag(member, flag, !claim.hasFlag(member, flag));
        saveClaims();
    }

    // ----- preview / criação -----

    /** Área 20x20 à frente do jogador (travada na direção cardeal). Retorna {minX,maxX,minZ,maxZ}. */
    public int[] areaInFront(Player player) {
        float yaw = (player.getLocation().getYaw() % 360 + 360) % 360;
        int dx;
        int dz;
        if (yaw >= 315 || yaw < 45) {
            dx = 0;
            dz = 1; // sul
        } else if (yaw < 135) {
            dx = -1;
            dz = 0; // oeste
        } else if (yaw < 225) {
            dx = 0;
            dz = -1; // norte
        } else {
            dx = 1;
            dz = 0; // leste
        }
        int rx = -dz; // perpendicular (largura)
        int rz = dx;
        int bx = player.getLocation().getBlockX();
        int bz = player.getLocation().getBlockZ();
        int half = size / 2;
        int near = nearOffset;
        int far = near + size - 1;
        int x1 = bx + dx * near + rx * (-half);
        int z1 = bz + dz * near + rz * (-half);
        int x2 = bx + dx * far + rx * (half - 1);
        int z2 = bz + dz * far + rz * (half - 1);
        return new int[]{Math.min(x1, x2), Math.max(x1, x2), Math.min(z1, z2), Math.max(z1, z2)};
    }

    /** Pode reivindicar essa área? (1 base por jogador, sem sobreposição). */
    public boolean canClaim(Player player, int[] area, String world) {
        if (hasClaim(player.getUniqueId())) {
            return false;
        }
        for (Claim c : claims.values()) {
            if (c.world().equals(world) && c.overlaps2D(area[0], area[1], area[2], area[3])) {
                return false;
            }
        }
        return true; // regras futuras (distância de construções, etc.) entram aqui
    }

    /** Clique direito no livro: trava a posição atual como pendente. */
    public void lockPending(Player player) {
        if (!enabled) {
            return;
        }
        if (hasClaim(player.getUniqueId())) {
            player.sendActionBar(Component.text("Você já tem uma base.", NamedTextColor.RED));
            return;
        }
        int[] area = areaInFront(player);
        String world = player.getWorld().getName();
        if (!canClaim(player, area, world)) {
            player.sendActionBar(Component.text("Não dá para reivindicar aqui (sobrepõe outra base).", NamedTextColor.RED));
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            return;
        }
        pending.put(player.getUniqueId(), new Pending(world, area[0], area[1], area[2], area[3]));
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
        player.sendMessage(Component.text("Posição travada! Digite ", NamedTextColor.GREEN)
                .append(Component.text("/confirmar base", NamedTextColor.GOLD))
                .append(Component.text(" para confirmar.", NamedTextColor.GREEN)));
    }

    /** /confirmar base: cria a base a partir da posição travada. */
    public void confirmPending(Player player) {
        if (!enabled) {
            return;
        }
        Pending p = pending.get(player.getUniqueId());
        if (p == null) {
            player.sendMessage(Component.text("Trave uma posição primeiro: segure o Livro de Base e clique direito.",
                    NamedTextColor.GRAY));
            return;
        }
        int[] area = {p.minX(), p.maxX(), p.minZ(), p.maxZ()};
        if (!canClaim(player, area, p.world())) {
            pending.remove(player.getUniqueId());
            player.sendMessage(Component.text("Não foi possível confirmar (a área não está mais livre).", NamedTextColor.RED));
            return;
        }
        World world = Bukkit.getWorld(p.world());
        if (world == null) {
            return;
        }
        // Desnível: menor e maior superfície na área -> caixa cobre tudo.
        int minSurface = Integer.MAX_VALUE;
        int maxSurface = Integer.MIN_VALUE;
        for (int x = p.minX(); x <= p.maxX(); x++) {
            for (int z = p.minZ(); z <= p.maxZ(); z++) {
                int y = world.getHighestBlockYAt(x, z);
                minSurface = Math.min(minSurface, y);
                maxSurface = Math.max(maxSurface, y);
            }
        }
        int minY = minSurface - heightDown;
        int maxY = maxSurface + heightUp;

        // Núcleo físico no centro (acesso ao menu da base).
        int cx = (p.minX() + p.maxX()) / 2;
        int cz = (p.minZ() + p.maxZ()) / 2;
        int cy = world.getHighestBlockYAt(cx, cz) + 1;
        world.getBlockAt(cx, cy, cz).setType(nucleoBlock);

        Claim claim = new Claim(player.getUniqueId(), p.world(),
                p.minX(), p.maxX(), p.minZ(), p.maxZ(), minY, maxY, cx, cy, cz);
        claims.put(player.getUniqueId(), claim);
        pending.remove(player.getUniqueId());
        saveClaims();
        consumeBook(player);

        player.playSound(player, Sound.ITEM_TOTEM_USE, 1f, 1.2f);
        player.sendMessage(Component.text("Base reivindicada! ", NamedTextColor.GREEN)
                .append(Component.text("(" + size + "x" + size + ", altura " + minY + " a " + maxY + ")",
                        NamedTextColor.GRAY)));
    }

    private void consumeBook(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        plugin.getItemRegistry().resolve(hand)
                .filter(ci -> ci.id().equals(BOOK_ID))
                .ifPresent(ci -> hand.setAmount(hand.getAmount() - 1));
    }

    /** Mostra as 4 extremidades da base por alguns segundos, só na tela do jogador. */
    public void showLimits(Player player) {
        Claim c = getClaim(player.getUniqueId());
        if (c == null) {
            player.sendActionBar(Component.text("Você não tem uma base.", NamedTextColor.RED));
            return;
        }
        World w = Bukkit.getWorld(c.world());
        if (w == null || !player.getWorld().equals(w)) {
            player.sendActionBar(Component.text("Sua base está em outro mundo.", NamedTextColor.GRAY));
            return;
        }
        int[][] corners = {
                {c.minX(), c.minZ()}, {c.minX(), c.maxZ()},
                {c.maxX(), c.minZ()}, {c.maxX(), c.maxZ()}
        };
        BlockData marker = markerBlock.createBlockData();
        List<Location> marked = new ArrayList<>();
        for (int[] corner : corners) {
            int surf = w.getHighestBlockYAt(corner[0], corner[1]);
            for (int dy = 1; dy <= markerHeight; dy++) {
                Location loc = new Location(w, corner[0], surf + dy, corner[1]);
                player.sendBlockChange(loc, marker); // só na tela dele (fake)
                marked.add(loc);
            }
        }
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
        player.sendActionBar(Component.text("Extremidades da base por " + markerSeconds + "s.", NamedTextColor.GREEN));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            for (Location loc : marked) {
                player.sendBlockChange(loc, loc.getBlock().getBlockData()); // volta ao real
            }
        }, markerSeconds * 20L);
    }

    // ----- preview -----

    private void previewTick() {
        if (!enabled) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Optional<CustomItem> held = plugin.getItemRegistry().resolve(player.getInventory().getItemInMainHand());
            if (held.isEmpty() || !(held.get() instanceof LivroDeBase)) {
                continue;
            }
            Pending p = pending.get(player.getUniqueId());
            if (p != null) {
                drawArea(player, p.minX(), p.maxX(), p.minZ(), p.maxZ(), GOLD);
            } else {
                int[] area = areaInFront(player);
                Color color = canClaim(player, area, player.getWorld().getName()) ? GREEN : RED;
                drawArea(player, area[0], area[1], area[2], area[3], color);
            }
        }
    }

    private void drawArea(Player player, int minX, int maxX, int minZ, int maxZ, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.4f);
        World w = player.getWorld();
        for (int x = minX; x <= maxX; x++) {
            surfaceParticle(player, w, x, minZ, dust);
            surfaceParticle(player, w, x, maxZ, dust);
        }
        for (int z = minZ; z <= maxZ; z++) {
            surfaceParticle(player, w, minX, z, dust);
            surfaceParticle(player, w, maxX, z, dust);
        }
    }

    private void surfaceParticle(Player player, World w, int x, int z, Particle.DustOptions dust) {
        int y = w.getHighestBlockYAt(x, z);
        player.spawnParticle(Particle.DUST, x + 0.5, y + 1.0, z + 0.5, 1, 0, 0, 0, 0, dust);
    }

    // ----- config / persistência -----

    private void loadConfig() {
        FileConfiguration c = configManager.loadConfig("claim.yml");
        this.enabled = c.getBoolean("enabled", true);
        this.size = Math.max(2, c.getInt("size", 20));
        this.heightUp = Math.max(1, c.getInt("height-up", 15));
        this.heightDown = Math.max(0, c.getInt("height-down", 1));
        this.nearOffset = c.getInt("near-offset", 5);
        this.previewInterval = Math.max(1, c.getInt("preview-interval-ticks", 2));
        this.restrictPlace = c.getBoolean("restrict-place", true);
        this.restrictBreak = c.getBoolean("restrict-break", true);
        this.markerHeight = Math.max(1, c.getInt("limits.marker-height", 6));
        this.markerSeconds = Math.max(1, c.getInt("limits.seconds", 10));
        Material m = Material.matchMaterial(c.getString("limits.block", "GLOWSTONE"));
        this.markerBlock = m != null ? m : Material.GLOWSTONE;
        Material n = Material.matchMaterial(c.getString("nucleo-block", "LODESTONE"));
        this.nucleoBlock = n != null ? n : Material.LODESTONE;
    }

    private void loadClaims() {
        claims.clear();
        if (!dataFile.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String world = cfg.getString(key + ".world");
                if (world == null) {
                    continue;
                }
                Claim claim = new Claim(uuid, world,
                        cfg.getInt(key + ".minX"), cfg.getInt(key + ".maxX"),
                        cfg.getInt(key + ".minZ"), cfg.getInt(key + ".maxZ"),
                        cfg.getInt(key + ".minY"), cfg.getInt(key + ".maxY"),
                        cfg.getInt(key + ".nucleoX"), cfg.getInt(key + ".nucleoY"), cfg.getInt(key + ".nucleoZ"));
                claim.setHeightUpgrades(cfg.getInt(key + ".heightUpgrades"));
                claim.setChestUpgrades(cfg.getInt(key + ".chestUpgrades"));
                var membersSec = cfg.getConfigurationSection(key + ".members");
                if (membersSec != null) {
                    for (String mk : membersSec.getKeys(false)) {
                        try {
                            UUID mu = UUID.fromString(mk);
                            claim.addMember(mu);
                            for (String f : membersSec.getStringList(mk)) {
                                claim.setFlag(mu, f, true);
                            }
                        } catch (IllegalArgumentException ignored) {
                            // membro inválido
                        }
                    }
                }
                claims.put(uuid, claim);
            } catch (IllegalArgumentException ignored) {
                // chave inválida
            }
        }
    }

    private void saveClaims() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Claim c : claims.values()) {
            String base = c.owner().toString();
            cfg.set(base + ".world", c.world());
            cfg.set(base + ".minX", c.minX());
            cfg.set(base + ".maxX", c.maxX());
            cfg.set(base + ".minZ", c.minZ());
            cfg.set(base + ".maxZ", c.maxZ());
            cfg.set(base + ".minY", c.minY());
            cfg.set(base + ".maxY", c.maxY());
            cfg.set(base + ".nucleoX", c.nucleoX());
            cfg.set(base + ".nucleoY", c.nucleoY());
            cfg.set(base + ".nucleoZ", c.nucleoZ());
            cfg.set(base + ".heightUpgrades", c.heightUpgrades());
            cfg.set(base + ".chestUpgrades", c.chestUpgrades());
            for (Map.Entry<UUID, java.util.Set<String>> e : c.members().entrySet()) {
                cfg.set(base + ".members." + e.getKey(), new ArrayList<>(e.getValue()));
            }
        }
        try {
            cfg.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Falha ao salvar claims.yml: " + ex.getMessage());
        }
    }

    private record Pending(String world, int minX, int maxX, int minZ, int maxZ) {
    }
}
