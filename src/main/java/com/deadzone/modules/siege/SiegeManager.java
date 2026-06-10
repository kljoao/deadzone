package com.deadzone.modules.siege;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Cerco: zumbis só quebram um bloco da base se VÁRIOS atacarem o mesmo bloco. */
public class SiegeManager implements Listener {

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, Siege> sieges = new HashMap<>();
    private final Set<Material> breakable = EnumSet.noneOf(Material.class);
    private BukkitTask task;

    private boolean enabled;
    private int scanInterval;
    private int minZombies;
    private double hitsToBreak;
    private long decayMs;
    private double scanRadius;
    private boolean dropOnBreak;
    private boolean reinforceEnabled;
    private int reinforcePerUse;
    private int reinforceMax;

    public SiegeManager(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        load();
    }

    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::scan, 20L, scanInterval);
    }

    public void disable() {
        if (task != null) {
            task.cancel();
        }
    }

    public void reload() {
        load();
    }

    private void load() {
        FileConfiguration c = configManager.loadConfig("siege.yml");
        this.enabled = c.getBoolean("enabled", true);
        this.scanInterval = Math.max(1, c.getInt("scan-interval-ticks", 10));
        this.minZombies = Math.max(1, c.getInt("min-zombies", 2));
        this.hitsToBreak = c.getDouble("hits-to-break", 20);
        this.decayMs = c.getLong("decay-seconds", 8) * 1000L;
        this.scanRadius = c.getDouble("scan-radius", 24);
        this.dropOnBreak = c.getBoolean("drop-on-break", false);
        this.reinforceEnabled = c.getBoolean("reinforce.enabled", true);
        this.reinforcePerUse = c.getInt("reinforce.per-use", 10);
        this.reinforceMax = c.getInt("reinforce.max", 60);
        breakable.clear();
        for (String name : c.getStringList("breakable")) {
            Material m = Material.matchMaterial(name);
            if (m != null) {
                breakable.add(m);
            }
        }
    }

    /** Blocos "frágeis" que os zumbis podem cercar (portas, cercas, vidro, tábuas + extras do config). */
    private boolean isBreakable(Block b) {
        Material m = b.getType();
        if (breakable.contains(m)) {
            return true;
        }
        if (Tag.DOORS.isTagged(m) || Tag.TRAPDOORS.isTagged(m) || Tag.FENCES.isTagged(m)
                || Tag.FENCE_GATES.isTagged(m) || Tag.PLANKS.isTagged(m)) {
            return true;
        }
        String n = m.name();
        return n.endsWith("GLASS") || n.endsWith("GLASS_PANE");
    }

    private void scan() {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        Set<Mob> sieging = new HashSet<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!isSurvival(p)) {
                continue;
            }
            for (Entity e : p.getNearbyEntities(scanRadius, scanRadius, scanRadius)) {
                if (e instanceof Mob mob && mob.getTarget() instanceof Player
                        && plugin.getInfectionManager().isZombieType(mob)) {
                    sieging.add(mob);
                }
            }
        }

        Map<String, Set<UUID>> attackers = new HashMap<>();
        Map<String, Block> blocks = new HashMap<>();
        for (Mob mob : sieging) {
            Vector horiz = mob.getVelocity().clone().setY(0);
            if (horiz.lengthSquared() > 0.01) {
                continue; // só conta zumbi "preso" contra o bloco (parado)
            }
            Block b = blockInFront(mob);
            if (b == null) {
                continue;
            }
            String k = key(b);
            attackers.computeIfAbsent(k, x -> new HashSet<>()).add(mob.getUniqueId());
            blocks.put(k, b);
            mob.swingMainHand();
        }

        for (Map.Entry<String, Set<UUID>> entry : attackers.entrySet()) {
            if (entry.getValue().size() >= minZombies) {
                damageBlock(blocks.get(entry.getKey()), now);
            }
        }
        decay(now);
        showBarricadeHp();
    }

    /** Agachado e olhando para um bloco frágil, mostra a "vida" (resistência) dele. */
    private void showBarricadeHp() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!p.isSneaking()) {
                continue;
            }
            Block b = p.getTargetBlockExact(5);
            if (b == null || !isBreakable(b)) {
                continue;
            }
            Siege s = sieges.get(key(b));
            double max = hitsToBreak + (s != null ? s.reinforced : 0);
            double dmg = s != null ? s.damage : 0;
            int remaining = (int) Math.ceil(Math.max(0, max - dmg));
            int maxInt = (int) Math.ceil(max);
            double ratio = maxInt <= 0 ? 1.0 : (double) remaining / maxInt;
            NamedTextColor color = ratio > 0.66 ? NamedTextColor.GREEN
                    : ratio > 0.33 ? NamedTextColor.GOLD : NamedTextColor.RED;
            p.sendActionBar(Component.text("🛡 Resistência: " + remaining + "/" + maxInt, color));
        }
    }

    private Block blockInFront(Mob mob) {
        if (!(mob.getTarget() instanceof Player target)) {
            return null;
        }
        Location loc = mob.getLocation();
        Vector dir = target.getLocation().toVector().subtract(loc.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 0.0001) {
            return null;
        }
        dir.normalize().multiply(0.6);
        Block feet = loc.clone().add(dir).getBlock();
        if (isBreakable(feet)) {
            return feet;
        }
        Block head = loc.clone().add(0, 1, 0).add(dir).getBlock();
        return isBreakable(head) ? head : null;
    }

    private void damageBlock(Block block, long now) {
        Siege s = sieges.computeIfAbsent(key(block), x -> new Siege(block));
        s.block = block;
        s.damage += 1;
        s.lastHit = now;
        Location c = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().playSound(c, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.8f, 0.7f);
        block.getWorld().spawnParticle(Particle.BLOCK, c, 8, 0.3, 0.3, 0.3, 0, block.getBlockData());
        if (s.damage >= hitsToBreak + s.reinforced) {
            breakBlock(block);
            sieges.remove(key(block));
        }
    }

    private void breakBlock(Block block) {
        Location c = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().playSound(c, Sound.BLOCK_WOOD_BREAK, 1f, 0.8f);
        block.getWorld().spawnParticle(Particle.BLOCK, c, 30, 0.4, 0.4, 0.4, 0, block.getBlockData());
        if (dropOnBreak) {
            block.breakNaturally();
        } else {
            block.setType(Material.AIR);
        }
    }

    private void decay(long now) {
        Iterator<Map.Entry<String, Siege>> it = sieges.entrySet().iterator();
        while (it.hasNext()) {
            Siege s = it.next().getValue();
            if (now - s.lastHit > decayMs) {
                s.damage -= 1;
                if (s.damage <= 0 && s.reinforced <= 0) {
                    it.remove();
                }
            }
        }
    }

    /** Barricar: agachar + clicar num bloco frágil com tábuas reforça (mais "vida"). */
    @EventHandler
    public void onReinforce(PlayerInteractEvent event) {
        if (!enabled || !reinforceEnabled || event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        ItemStack hand = event.getItem();
        if (hand == null || !Tag.PLANKS.isTagged(hand.getType())) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !isBreakable(block)) {
            return;
        }
        event.setCancelled(true);
        Siege s = sieges.computeIfAbsent(key(block), x -> new Siege(block));
        s.block = block;
        if (s.reinforced >= reinforceMax) {
            player.sendActionBar(Component.text("Barricada no máximo.", NamedTextColor.GRAY));
            return;
        }
        s.reinforced = Math.min(reinforceMax, s.reinforced + reinforcePerUse);
        s.damage = Math.max(0, s.damage - reinforcePerUse);
        hand.setAmount(hand.getAmount() - 1);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOOD_PLACE, 1f, 0.9f);
        player.sendActionBar(Component.text("Barricada reforçada (" + s.reinforced + "/" + reinforceMax + ")",
                NamedTextColor.GREEN));
    }

    private boolean isSurvival(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }

    private String key(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    private static final class Siege {
        private Block block;
        private double damage;
        private int reinforced;
        private long lastHit;

        private Siege(Block block) {
            this.block = block;
        }
    }
}
