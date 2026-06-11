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
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
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
    private BukkitTask saveTask;
    private boolean dirty;
    private final LockedChestManager lockedChests;

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
    private int heightMaxUpgrades;
    private int heightBlocks;
    private int heightCostXp;
    private int chestMaxUpgrades;
    private int chestCostXp;
    private int baseChests;
    private int chestsPerUpgrade;

    public ClaimManager(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dataFile = new File(plugin.getDataFolder(), "claims.yml");
        loadConfig();
        loadClaims();
        this.lockedChests = new LockedChestManager(plugin, this);
    }

    public LockedChestManager lockedChests() {
        return lockedChests;
    }

    /** Limite de baús físicos da base. */
    public int chestLimit(Claim claim) {
        return baseChests + claim.chestUpgrades() * chestsPerUpgrade;
    }

    private void giveChests(Player player, int amount) {
        if (amount > 0) {
            player.getInventory().addItem(new ItemStack(Material.CHEST, amount));
        }
    }

    public void enable() {
        plugin.getItemRegistry().register(new LivroDeBase(plugin, this));
        plugin.getServer().getPluginManager().registerEvents(new ClaimListener(plugin, this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new LockedChestListener(plugin, this), plugin);
        plugin.getTickService().registerSecondHandler(this::baseTick);
        previewTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::previewTick, previewInterval, previewInterval);
        // Flush periódico (blocos construídos mudam muito; não salva a cada bloco).
        saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                saveClaims();
            }
        }, 600L, 600L);
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
        if (saveTask != null) {
            saveTask.cancel();
        }
        saveClaims();
    }

    /** Registra um bloco construído dentro da base (para apagar a construção ao remover). */
    public void recordPlace(Claim claim, Block block) {
        claim.placed().add(packRel(claim, block.getX(), block.getY(), block.getZ()));
        dirty = true;
    }

    public void recordBreak(Claim claim, Block block) {
        if (claim.placed().remove(packRel(claim, block.getX(), block.getY(), block.getZ()))) {
            dirty = true;
        }
    }

    private int packRel(Claim c, int x, int y, int z) {
        return ((y - c.minY()) << 12) | ((x - c.minX()) << 6) | (z - c.minZ());
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
        // Também guardamos o relevo natural por coluna (p/ apagar só a construção na remoção).
        int minSurface = Integer.MAX_VALUE;
        int maxSurface = Integer.MIN_VALUE;
        int width = p.maxX() - p.minX() + 1;
        int depth = p.maxZ() - p.minZ() + 1;
        int[] ground = new int[width * depth];
        for (int x = p.minX(); x <= p.maxX(); x++) {
            for (int z = p.minZ(); z <= p.maxZ(); z++) {
                int y = world.getHighestBlockYAt(x, z);
                minSurface = Math.min(minSurface, y);
                maxSurface = Math.max(maxSurface, y);
                ground[(x - p.minX()) * depth + (z - p.minZ())] = y;
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
        claim.setGround(ground);
        claims.put(player.getUniqueId(), claim);
        pending.remove(player.getUniqueId());
        saveClaims();
        consumeBook(player);
        giveChests(player, baseChests); // baús iniciais para colocar na base

        player.playSound(player, Sound.ITEM_TOTEM_USE, 1f, 1.2f);
        player.sendMessage(Component.text("Base reivindicada! ", NamedTextColor.GREEN)
                .append(Component.text("(" + size + "x" + size + ", " + baseChests + " baús)",
                        NamedTextColor.GRAY)));
    }

    private void consumeBook(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        plugin.getItemRegistry().resolve(hand)
                .filter(ci -> ci.id().equals(BOOK_ID))
                .ifPresent(ci -> hand.setAmount(hand.getAmount() - 1));
    }

    /** Remove a base: apaga a construção e TODOS os itens/baús, tira o núcleo e devolve um Livro de Base. */
    public void removeClaim(Player owner, Claim claim) {
        World w = Bukkit.getWorld(claim.world());
        if (w != null) {
            clearConstruction(w, claim);
            Block nucleo = w.getBlockAt(claim.nucleoX(), claim.nucleoY(), claim.nucleoZ());
            if (nucleo.getType() == nucleoBlock) {
                nucleo.setType(Material.AIR, false);
            }
        }
        claim.placed().clear();
        lockedChests.removeLocksInClaim(claim); // remove as trancas dos baús (conteúdo é apagado)
        claims.remove(claim.owner());
        inBase.remove(claim.owner());
        saveClaims();
        owner.closeInventory();
        owner.getInventory().addItem(new LivroDeBase(plugin, this).build());
        owner.playSound(owner, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
        owner.sendMessage(Component.text("Base removida — construção e itens apagados. "
                + "Você recebeu um Livro de Base.", NamedTextColor.YELLOW));
    }

    /** Apaga a construção: tudo acima do solo natural + blocos colocados. Sem relevo salvo, limpa a caixa. */
    private void clearConstruction(World w, Claim claim) {
        int[] ground = claim.ground();
        int width = claim.maxX() - claim.minX() + 1;
        int depth = claim.maxZ() - claim.minZ() + 1;
        if (ground != null && ground.length == width * depth) {
            for (int rx = 0; rx < width; rx++) {
                for (int rz = 0; rz < depth; rz++) {
                    for (int y = ground[rx * depth + rz] + 1; y <= claim.maxY(); y++) {
                        clearAir(w, claim.minX() + rx, y, claim.minZ() + rz);
                    }
                }
            }
            for (int key : claim.placed()) { // blocos colocados ao nível/abaixo do solo
                clearAir(w, claim.minX() + ((key >> 6) & 0x3F), claim.minY() + (key >> 12),
                        claim.minZ() + (key & 0x3F));
            }
        } else {
            // Base antiga (sem relevo salvo): limpa a caixa inteira.
            for (int x = claim.minX(); x <= claim.maxX(); x++) {
                for (int z = claim.minZ(); z <= claim.maxZ(); z++) {
                    for (int y = claim.minY(); y <= claim.maxY(); y++) {
                        clearAir(w, x, y, z);
                    }
                }
            }
        }
    }

    private void clearAir(World w, int x, int y, int z) {
        Block b = w.getBlockAt(x, y, z);
        if (!b.getType().isAir()) {
            b.setType(Material.AIR, false);
        }
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
        this.heightMaxUpgrades = Math.max(0, c.getInt("upgrades.height.max", 4));
        this.heightBlocks = Math.max(1, c.getInt("upgrades.height.blocks", 4));
        this.heightCostXp = Math.max(0, c.getInt("upgrades.height.cost-xp", 100));
        this.chestMaxUpgrades = Math.max(0, c.getInt("upgrades.chest.max", 20));
        this.chestCostXp = Math.max(0, c.getInt("upgrades.chest.cost-xp", 50));
        this.baseChests = Math.max(0, c.getInt("chests.base", 4));
        this.chestsPerUpgrade = Math.max(1, c.getInt("chests.per-upgrade", 2));
    }

    public int heightMaxUpgrades() {
        return heightMaxUpgrades;
    }

    public int heightBlocks() {
        return heightBlocks;
    }

    public int heightCostXp() {
        return heightCostXp;
    }

    public int chestMaxUpgrades() {
        return chestMaxUpgrades;
    }

    public int chestCostXp() {
        return chestCostXp;
    }

    public boolean upgradeHeight(Player player, Claim claim) {
        if (claim.heightUpgrades() >= heightMaxUpgrades) {
            player.sendActionBar(Component.text("Altura já no máximo.", NamedTextColor.GRAY));
            return false;
        }
        if (!spendXp(player, heightCostXp)) {
            return false;
        }
        claim.setMaxY(claim.maxY() + heightBlocks);
        claim.setHeightUpgrades(claim.heightUpgrades() + 1);
        saveClaims();
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        return true;
    }

    public boolean upgradeChests(Player player, Claim claim) {
        if (claim.chestUpgrades() >= chestMaxUpgrades) {
            player.sendActionBar(Component.text("Já no máximo de baús.", NamedTextColor.GRAY));
            return false;
        }
        if (!spendXp(player, chestCostXp)) {
            return false;
        }
        claim.setChestUpgrades(claim.chestUpgrades() + 1);
        saveClaims();
        giveChests(player, chestsPerUpgrade); // entrega os baús extras
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        return true;
    }

    private boolean spendXp(Player player, int cost) {
        if (cost <= 0) {
            return true;
        }
        PlayerProfile p = plugin.getProfileManager().get(player.getUniqueId());
        if (p == null) {
            return false;
        }
        if (p.getXp() < cost) {
            player.sendActionBar(Component.text("XP insuficiente (precisa de " + cost + ").", NamedTextColor.RED));
            return false;
        }
        p.setXp(p.getXp() - cost);
        return true;
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
                String placedStr = cfg.getString(key + ".placed");
                if (placedStr != null) {
                    ByteBuffer pb = ByteBuffer.wrap(Base64.getDecoder().decode(placedStr));
                    while (pb.remaining() >= 4) {
                        claim.placed().add(pb.getInt());
                    }
                }
                String groundStr = cfg.getString(key + ".ground");
                if (groundStr != null) {
                    ByteBuffer gb = ByteBuffer.wrap(Base64.getDecoder().decode(groundStr));
                    int[] g = new int[gb.remaining() / 4];
                    for (int i = 0; i < g.length; i++) {
                        g[i] = gb.getInt();
                    }
                    claim.setGround(g);
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
            if (!c.placed().isEmpty()) {
                ByteBuffer buf = ByteBuffer.allocate(c.placed().size() * 4);
                for (int k : c.placed()) {
                    buf.putInt(k);
                }
                cfg.set(base + ".placed", Base64.getEncoder().encodeToString(buf.array()));
            }
            if (c.ground() != null) {
                ByteBuffer gb = ByteBuffer.allocate(c.ground().length * 4);
                for (int g : c.ground()) {
                    gb.putInt(g);
                }
                cfg.set(base + ".ground", Base64.getEncoder().encodeToString(gb.array()));
            }
        }
        try {
            cfg.save(dataFile);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Falha ao salvar claims.yml: " + ex.getMessage());
        }
    }

    private record Pending(String world, int minX, int maxX, int minZ, int maxZ) {
    }
}
