package com.deadzone.modules.firearms;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.entity.EntityKeys;
import com.deadzone.core.item.CustomItem;
import com.deadzone.core.item.ItemKeys;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.core.scheduler.TickService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Carrega as armas do firearms.yml e cuida do disparo/recarga (projétil = bola de neve). */
public class FirearmManager {

    private final DeadzonePlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, FirearmType> types = new LinkedHashMap<>();
    private final Map<UUID, Long> lastShot = new HashMap<>();
    private final Map<UUID, Long> reloadingUntil = new HashMap<>();
    private final Map<UUID, Double> recoilHeat = new HashMap<>();
    private final Map<UUID, Long> meleeCooldown = new HashMap<>();
    private final Map<UUID, Long> drawUntil = new HashMap<>();
    private final Map<String, AmmoType> ammoTypes = new LinkedHashMap<>();
    private boolean enabled;

    private static final int DRAW_OFFSET = 100;
    private static final Particle.DustOptions TRACER = new Particle.DustOptions(Color.fromRGB(255, 235, 160), 0.6f);

    public FirearmManager(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void enable(TickService tickService) {
        load();
        for (String id : types.keySet()) {
            plugin.getItemRegistry().register(new FirearmItem(plugin, this, id));
        }
        for (AmmoType ammo : ammoTypes.values()) {
            plugin.getItemRegistry().register(new AmmoItem(ammo));
        }
        plugin.getServer().getPluginManager().registerEvents(new FirearmListener(plugin, this), plugin);
        tickService.registerSecondHandler(this::tickHud);
    }

    public void reload() {
        load();
    }

    public boolean enabled() {
        return enabled;
    }

    public FirearmType type(String id) {
        return types.get(id);
    }

    public void shoot(Player player, FirearmItem item, ItemStack gun) {
        if (!enabled) {
            return;
        }
        FirearmType type = item.type();
        if (type == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now < drawUntil.getOrDefault(uuid, 0L)) {
            return;
        }
        if (now < reloadingUntil.getOrDefault(uuid, 0L)) {
            return;
        }
        Long last = lastShot.get(uuid);
        if (last != null && now - last < type.fireCooldownMs()) {
            return;
        }
        int ammo = readAmmo(gun, type);
        if (ammo <= 0) {
            startReload(player, item, gun);
            return;
        }
        double spread = nextSpread(uuid, type, now, last);
        lastShot.put(uuid, now);
        ammo--;
        writeAmmo(gun, ammo);
        player.getInventory().setItemInMainHand(gun);

        fireProjectile(player, type, spread);
        player.getWorld().playSound(player.getLocation(), type.soundShoot(), SoundCategory.PLAYERS, 1.4f, 1f);
        plugin.getNoiseManager().report(player, type.noise(), type.noiseRadius());
        showAmmo(player, type, ammo);

        if (ammo == 0) {
            startReload(player, item, gun);
        }
    }

    public void startReload(Player player, FirearmItem item, ItemStack gun) {
        if (!enabled) {
            return;
        }
        FirearmType type = item.type();
        if (type == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now < reloadingUntil.getOrDefault(uuid, 0L)) {
            return;
        }
        int current = readAmmo(gun, type);
        if (type.magazineSize() - current <= 0) {
            return;
        }
        if (countAmmo(player, type.ammoItem()) <= 0) {
            player.playSound(player, Sound.BLOCK_DISPENSER_FAIL, 1f, 1.4f);
            player.sendActionBar(Component.text("Sem munição.", NamedTextColor.RED));
            return;
        }
        reloadingUntil.put(uuid, now + type.reloadMs());
        player.getWorld().playSound(player.getLocation(), type.soundReload(), SoundCategory.PLAYERS, 1.2f, 1f);
        player.sendActionBar(Component.text("Recarregando...", NamedTextColor.YELLOW));
        long ticks = Math.max(1, type.reloadMs() / 50);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> finishReload(player, item, type), ticks);
    }

    private void finishReload(Player player, FirearmItem item, FirearmType type) {
        reloadingUntil.remove(player.getUniqueId());
        if (!player.isOnline()) {
            return;
        }
        ItemStack gun = player.getInventory().getItemInMainHand();
        if (plugin.getItemRegistry().resolve(gun).filter(ci -> ci.id().equals(item.id())).isEmpty()) {
            return;
        }
        int current = readAmmo(gun, type);
        int take = Math.min(type.magazineSize() - current, countAmmo(player, type.ammoItem()));
        if (take <= 0) {
            return;
        }
        removeAmmo(player, type.ammoItem(), take);
        int total = current + take;
        writeAmmo(gun, total);
        player.getInventory().setItemInMainHand(gun);
        showAmmo(player, type, total);
    }

    private void fireProjectile(Player player, FirearmType type, double spread) {
        Location eye = player.getEyeLocation();
        Vector dir = applyConeSpread(eye.getDirection(), spread);
        Snowball ball = player.launchProjectile(Snowball.class, dir.clone().multiply(type.projectileSpeed()));
        ball.setGravity(false);
        ball.setVisibleByDefault(false); // projétil invisível; o visual é o rastro (tracer)
        ball.getPersistentDataContainer().set(EntityKeys.FIREARM_BULLET, PersistentDataType.DOUBLE, type.damage());
        double drop = type.bulletDrop();
        new BukkitRunnable() {
            Location prev = ball.getLocation();

            @Override
            public void run() {
                if (ball.isDead() || !ball.isValid()) {
                    cancel();
                    return;
                }
                Location cur = ball.getLocation();
                tracer(prev, cur);
                prev = cur.clone();
                if (drop > 0) {
                    Vector v = ball.getVelocity();
                    v.setY(v.getY() - drop);
                    ball.setVelocity(v);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        player.getWorld().spawnParticle(Particle.SMALL_FLAME, eye.add(dir.clone().multiply(1.2)), 1, 0, 0, 0, 0);
    }

    /** Desenha o rastro do projétil (linha de partículas) do ponto anterior ao atual. */
    private void tracer(Location from, Location to) {
        if (from.getWorld() != to.getWorld()) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        double dist = delta.length();
        if (dist < 0.05 || dist > 64) {
            return;
        }
        Vector step = delta.clone().normalize().multiply(0.4);
        Location p = from.clone();
        for (double d = 0; d < dist; d += 0.4) {
            from.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, TRACER);
            p.add(step);
        }
    }

    /**
     * Recoil por dispersão: a cada tiro o "calor" sobe (mais espalhamento) e
     * recupera com o tempo parado. Não mexe na câmera nem na posição do jogador.
     */
    private double nextSpread(UUID uuid, FirearmType type, long now, Long last) {
        double heat = recoilHeat.getOrDefault(uuid, 0.0);
        if (last != null) {
            double elapsed = (now - last) / 1000.0;
            heat = Math.max(0.0, heat - type.spreadRecovery() * elapsed);
        } else {
            heat = 0.0;
        }
        double spread = type.spreadBase() + heat;
        recoilHeat.put(uuid, Math.min(type.spreadMax(), heat + type.spreadPerShot()));
        return spread;
    }

    private Vector applyConeSpread(Vector dir, double degrees) {
        if (degrees <= 0) {
            return dir;
        }
        double rad = Math.toRadians(degrees);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        return dir.clone().add(new Vector(
                (rng.nextDouble() - 0.5) * rad,
                (rng.nextDouble() - 0.5) * rad,
                (rng.nextDouble() - 0.5) * rad)).normalize();
    }

    /**
     * Inicia o saque: esconde a arma durante o delay e a revela (com a animação
     * nativa de equipar) ao terminar. Não pode atirar durante.
     */
    public void startDraw(Player player, FirearmType type, int slot) {
        if (type.drawMs() <= 0) {
            return;
        }
        drawUntil.put(player.getUniqueId(), System.currentTimeMillis() + type.drawMs());
        player.sendActionBar(Component.text("Sacando a arma...", NamedTextColor.YELLOW));

        ItemStack gun = player.getInventory().getItem(slot);
        if (gun == null) {
            return;
        }
        int base = type.customModelData();
        setModelData(gun, base + DRAW_OFFSET);
        player.getInventory().setItem(slot, gun);

        long ticks = Math.max(1, type.drawMs() / 50);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> revealGun(player, base, slot), ticks);
    }

    /** Revela a arma ao fim do saque (se ainda estiver no modelo de saque). */
    private void revealGun(Player player, int base, int slot) {
        if (!player.isOnline()) {
            return;
        }
        ItemStack gun = player.getInventory().getItem(slot);
        if (isDrawing(gun, base)) {
            setModelData(gun, base);
            player.getInventory().setItem(slot, gun);
        }
    }

    /** Revela a arma no slot se estiver no modelo de saque (ex.: trocar de item no meio). */
    public void restoreHidden(Player player, int slot) {
        ItemStack gun = player.getInventory().getItem(slot);
        Integer base = baseOf(gun);
        if (base != null && isDrawing(gun, base)) {
            setModelData(gun, base);
            player.getInventory().setItem(slot, gun);
            drawUntil.remove(player.getUniqueId());
        }
    }

    private Integer baseOf(ItemStack gun) {
        return plugin.getItemRegistry().resolve(gun)
                .filter(ci -> ci instanceof FirearmItem)
                .map(ci -> ((FirearmItem) ci).type())
                .map(t -> t == null ? null : t.customModelData())
                .orElse(null);
    }

    private boolean isDrawing(ItemStack gun, int base) {
        return gun != null && gun.hasItemMeta() && gun.getItemMeta().hasCustomModelData()
                && gun.getItemMeta().getCustomModelData() == base + DRAW_OFFSET;
    }

    private void setModelData(ItemStack gun, int modelData) {
        gun.editMeta(meta -> meta.setCustomModelData(modelData));
    }

    private Material parseMaterial(String name, Material def) {
        Material m = name == null ? null : Material.matchMaterial(name);
        return m != null ? m : def;
    }

    /** Tenta dar a coronhada; false se ainda em cooldown (mostra o timer). */
    public boolean tryMeleeHit(Player player, FirearmItem item) {
        FirearmType type = item.type();
        if (type == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long until = meleeCooldown.getOrDefault(uuid, 0L);
        if (now < until) {
            player.sendActionBar(Component.text("Coronhada recarregando: " + ((until - now + 999) / 1000) + "s",
                    NamedTextColor.GRAY));
            player.playSound(player, Sound.BLOCK_LEVER_CLICK, 0.6f, 0.8f);
            return false;
        }
        meleeCooldown.put(uuid, now + (long) (type.meleeCooldownSeconds() * 1000));
        return true;
    }

    /** Concussão: lentidão máxima + brilho + estrelinhas na cabeça do alvo. */
    public void applyConcussion(Player player, FirearmItem item, LivingEntity target) {
        FirearmType type = item.type();
        if (type == null) {
            return;
        }
        int ticks = type.meleeSlowSeconds() * 20;
        if (ticks > 0) {
            PotionEffectType slowness = Registry.EFFECT.get(NamespacedKey.minecraft("slowness"));
            if (slowness != null) {
                target.addPotionEffect(new PotionEffect(slowness, ticks, type.meleeSlowAmplifier(), false, false, true));
            }
            PotionEffectType glowing = Registry.EFFECT.get(NamespacedKey.minecraft("glowing"));
            if (glowing != null) {
                target.addPotionEffect(new PotionEffect(glowing, ticks, 0, false, false, true));
            }
            concussionParticles(target, ticks);
        }
        player.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
        player.getWorld().playSound(target.getLocation(), Sound.ENTITY_ZOMBIE_HURT, 1f, 0.7f);
    }

    private void concussionParticles(LivingEntity target, int ticks) {
        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= ticks || target.isDead() || !target.isValid()) {
                    cancel();
                    return;
                }
                Location head = target.getLocation().add(0, target.getHeight() + 0.4, 0);
                target.getWorld().spawnParticle(Particle.CRIT, head, 6, 0.25, 0.1, 0.25, 0.0);
                elapsed += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private int readAmmo(ItemStack gun, FirearmType type) {
        if (!gun.hasItemMeta()) {
            return type.magazineSize();
        }
        Integer v = gun.getItemMeta().getPersistentDataContainer().get(ItemKeys.FIREARM_AMMO, PersistentDataType.INTEGER);
        return v == null ? type.magazineSize() : v;
    }

    private void writeAmmo(ItemStack gun, int ammo) {
        gun.editMeta(meta -> meta.getPersistentDataContainer().set(ItemKeys.FIREARM_AMMO, PersistentDataType.INTEGER, ammo));
    }

    private boolean isAmmo(ItemStack s, String ammoId) {
        if (s == null || !s.hasItemMeta()) {
            return false;
        }
        String id = s.getItemMeta().getPersistentDataContainer().get(ItemKeys.ITEM_ID, PersistentDataType.STRING);
        return ammoId.equals(id);
    }

    private int countAmmo(Player player, String ammoId) {
        int n = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (isAmmo(s, ammoId)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    private void removeAmmo(Player player, String ammoId, int amount) {
        var inv = player.getInventory();
        int remaining = amount;
        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (isAmmo(s, ammoId)) {
                int take = Math.min(remaining, s.getAmount());
                s.setAmount(s.getAmount() - take);
                inv.setItem(i, s.getAmount() > 0 ? s : null);
                remaining -= take;
            }
        }
    }

    private void showAmmo(Player player, FirearmType type, int ammo) {
        player.sendActionBar(ammoComponent(type, ammo));
    }

    private Component ammoComponent(FirearmType type, int ammo) {
        NamedTextColor color = ammo == 0 ? NamedTextColor.RED
                : ammo <= Math.max(1, type.magazineSize() / 3) ? NamedTextColor.GOLD : NamedTextColor.WHITE;
        return Component.text(ammo + "/" + type.magazineSize(), color);
    }

    /** HUD fixo de munição enquanto segura a arma (action bar, logo acima da vida). */
    private void tickHud(PlayerProfile profile) {
        if (!enabled) {
            return;
        }
        Player player = plugin.getServer().getPlayer(profile.getUuid());
        if (player == null) {
            return;
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        Optional<CustomItem> resolved = plugin.getItemRegistry().resolve(main);
        if (resolved.isEmpty() || !(resolved.get() instanceof FirearmItem firearm)) {
            return;
        }
        FirearmType type = firearm.type();
        if (type == null) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID uuid = profile.getUuid();
        if (now < drawUntil.getOrDefault(uuid, 0L)) {
            player.sendActionBar(Component.text("Sacando a arma...", NamedTextColor.YELLOW));
        } else if (now < reloadingUntil.getOrDefault(uuid, 0L)) {
            player.sendActionBar(Component.text("Recarregando...", NamedTextColor.YELLOW));
        } else {
            player.sendActionBar(hudLine(uuid, type, readAmmo(main, type)));
        }
    }

    private Component hudLine(UUID uuid, FirearmType type, int ammo) {
        Component line = ammoComponent(type, ammo);
        long remaining = meleeCooldown.getOrDefault(uuid, 0L) - System.currentTimeMillis();
        if (remaining > 0) {
            line = line.append(Component.text("   coronhada: " + ((remaining + 999) / 1000) + "s", NamedTextColor.GRAY));
        }
        return line;
    }

    private void load() {
        types.clear();
        FileConfiguration c = configManager.loadConfig("firearms.yml");
        this.enabled = c.getBoolean("enabled", true);
        ammoTypes.clear();
        ConfigurationSection ammoSec = c.getConfigurationSection("ammo");
        if (ammoSec != null) {
            for (String aid : ammoSec.getKeys(false)) {
                ConfigurationSection a = ammoSec.getConfigurationSection(aid);
                if (a == null) {
                    continue;
                }
                ammoTypes.put(aid, new AmmoType(
                        aid,
                        parseMaterial(a.getString("material", "IRON_NUGGET"), Material.IRON_NUGGET),
                        a.getInt("model-data"),
                        a.getString("display-name", aid),
                        a.getStringList("lore")));
            }
        }
        ConfigurationSection guns = c.getConfigurationSection("guns");
        if (guns == null) {
            return;
        }
        for (String id : guns.getKeys(false)) {
            ConfigurationSection s = guns.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            types.put(id, new FirearmType(
                    id,
                    s.getInt("model-data"),
                    s.getString("display-name", id),
                    s.getStringList("lore"),
                    s.getString("ammo-item", "9mm"),
                    s.getDouble("damage", 4.0),
                    s.getDouble("projectile-speed", 4.0),
                    s.getDouble("bullet-drop", 0.015),
                    s.getLong("fire-cooldown-ms", 700),
                    Math.max(1, s.getInt("magazine-size", 17)),
                    s.getLong("reload-ms", 1600),
                    s.getLong("draw-ms", 1400),
                    s.getDouble("spread-base", 0.5),
                    s.getDouble("spread-per-shot", 0.3),
                    s.getDouble("spread-max", 3.0),
                    s.getDouble("spread-recovery", 6.0),
                    s.getDouble("melee-damage", 4.0),
                    s.getDouble("melee-cooldown-seconds", 8.0),
                    s.getInt("melee-slow-seconds", 5),
                    s.getInt("melee-slow-amplifier", 6),
                    s.getString("sound-shoot", "deadzone:firearm.g17.shoot"),
                    s.getString("sound-reload", "deadzone:firearm.g17.reload"),
                    s.getDouble("noise", 30.0),
                    s.getInt("noise-radius", 40)));
        }
    }
}
