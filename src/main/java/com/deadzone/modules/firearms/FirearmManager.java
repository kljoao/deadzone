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
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final Map<UUID, BukkitTask> autoTasks = new HashMap<>();
    private final Map<UUID, Integer> burstAmmo = new HashMap<>(); // munição "ao vivo" durante a rajada
    private final Set<UUID> aiming = new HashSet<>(); // jogadores mirando (ADS = Slowness)
    private final Map<String, AmmoType> ammoTypes = new LinkedHashMap<>();
    private FirearmSkinsConfig skinsConfig;
    private boolean enabled;

    private static final int DRAW_OFFSET = 100;
    // Mira com luneta: Slowness altíssima = jogador imóvel e zoom no máximo (sniper).
    private static final int SCOPE_ADS_AMPLIFIER = 250;
    private static final Particle.DustOptions TRACER = new Particle.DustOptions(Color.fromRGB(255, 235, 160), 0.6f);

    public FirearmManager(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void enable(TickService tickService) {
        load();
        this.skinsConfig = new FirearmSkinsConfig(plugin, configManager);
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
        if (skinsConfig != null) {
            skinsConfig.load();
        }
    }

    public FirearmSkinsConfig skins() {
        return skinsConfig;
    }

    public boolean enabled() {
        return enabled;
    }

    public FirearmType type(String id) {
        return types.get(id);
    }

    /** @return true se o evento de clique deve ser cancelado (semi). No auto retorna false p/ deixar o "uso" começar. */
    public boolean shoot(Player player, FirearmItem item, ItemStack gun) {
        if (!enabled) {
            return true;
        }
        FirearmType type = item.type();
        if (type == null) {
            return true;
        }
        // Modo automático: segurar o botão direito = rajada (detecta via mão levantada / "uso" do item).
        if (type.automatic() && readFireMode(gun) == FireMode.AUTO) {
            startHoldFire(player, item);
            return false; // NÃO cancela: deixa o "uso" (mordida) iniciar p/ detectar o segurar.
        }
        // Semiauto: 1 clique = 1 tiro. Se a arma ficou com o componente de "comida" (resíduo do modo
        // auto), tira aqui: senão segurar o direito re-inicia a "mordida" e dispara mais de um tiro.
        if (gun.hasItemMeta() && gun.getItemMeta().hasFood()) {
            gun.editMeta(m -> m.setFood(null));
            player.getInventory().setItemInMainHand(gun);
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastShot.get(uuid);
        if (last != null && now - last < type.fireCooldownMs()) {
            return true;
        }
        fireOnce(player, item, gun);
        return true;
    }

    /** Semiauto: dispara 1 tiro (checa saque/recarga/munição). Grava a munição no item. */
    private boolean fireOnce(Player player, FirearmItem item, ItemStack gun) {
        FirearmType type = item.type();
        if (type == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now < drawUntil.getOrDefault(uuid, 0L) || now < reloadingUntil.getOrDefault(uuid, 0L)) {
            return false;
        }
        int ammo = readAmmo(gun, type);
        if (ammo <= 0) {
            startReload(player, item, gun);
            return false;
        }
        double spread = nextSpread(uuid, type, now, lastShot.get(uuid));
        lastShot.put(uuid, now);
        ammo--;
        writeAmmo(gun, ammo);
        player.getInventory().setItemInMainHand(gun);
        fireShot(player, type, spread);
        showAmmo(player, type, ammo);
        if (ammo == 0) {
            startReload(player, item, gun);
            return false;
        }
        return true;
    }

    /** Efeitos de um disparo: projétil + som + barulho. Não mexe em munição. */
    private void fireShot(Player player, FirearmType type, double spread) {
        fireProjectile(player, type, spread);
        player.getWorld().playSound(player.getLocation(), type.soundShoot(), SoundCategory.PLAYERS, 1.4f, 1f);
        plugin.getNoiseManager().report(player, type.noise(), type.noiseRadius());
    }

    // ---- Seletor de modo de disparo (semi/auto) ----

    enum FireMode {SEMI, AUTO}

    private FireMode readFireMode(ItemStack gun) {
        if (gun == null || !gun.hasItemMeta()) {
            return FireMode.SEMI;
        }
        String v = gun.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.FIREARM_MODE, PersistentDataType.STRING);
        return "AUTO".equals(v) ? FireMode.AUTO : FireMode.SEMI;
    }

    /** Alterna semi/auto (só p/ armas automáticas). Disparado por sneak + F. */
    public void toggleFireMode(Player player, FirearmItem item, ItemStack gun) {
        FirearmType type = item.type();
        if (type == null || !type.automatic()) {
            return;
        }
        FireMode next = readFireMode(gun) == FireMode.SEMI ? FireMode.AUTO : FireMode.SEMI;
        boolean auto = next == FireMode.AUTO;
        gun.editMeta(meta -> {
            meta.getPersistentDataContainer().set(ItemKeys.FIREARM_MODE, PersistentDataType.STRING, next.name());
            applyAutoFood(meta, auto);
        });
        player.getInventory().setItemInMainHand(gun);
        if (!auto) {
            endBurst(player.getUniqueId());
        }
        player.sendActionBar(Component.text("Modo: " + (auto ? "Automático ⟶ rajada" : "Semiauto"),
                auto ? NamedTextColor.GOLD : NamedTextColor.AQUA));
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.8f, auto ? 1.5f : 0.7f);
    }

    /**
     * Rajada enquanto segura o botão direito. O modo AUTO marca a arma como "consumível"
     * (componente de comida), então segurar o direito mantém a mão levantada
     * ({@link Player#isHandRaised()}); o loop dispara enquanto isso for verdade.
     */
    private void startHoldFire(Player player, FirearmItem item) {
        UUID uuid = player.getUniqueId();
        if (autoTasks.containsKey(uuid)) {
            return; // já metralhando
        }
        FirearmType type = item.type();
        // Munição "ao vivo" da rajada: lê 1x e mantém em memória. NÃO mexer no item durante o segurar,
        // senão o update de slot cancela o "uso" (comer) -> a mão abaixa -> a rajada para.
        burstAmmo.put(uuid, readAmmo(player.getInventory().getItemInMainHand(), type));
        long cooldown = Math.max(1L, type.fireCooldownMs());
        BukkitTask task = new BukkitRunnable() {
            boolean firstTick = true;
            long nextShot = 0L;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    endBurst(uuid);
                    return;
                }
                ItemStack gun = player.getInventory().getItemInMainHand();
                if (plugin.getItemRegistry().resolve(gun).filter(ci -> ci.id().equals(item.id())).isEmpty()
                        || readFireMode(gun) != FireMode.AUTO) {
                    endBurst(uuid);
                    return;
                }
                // No 1º tick a mão pode ainda não ter subido (mesmo tick do clique): solta 1 tiro.
                // Nos seguintes, só continua enquanto o jogador estiver segurando (mão levantada).
                if (!firstTick && !player.isHandRaised()) {
                    endBurst(uuid);
                    return;
                }
                long now = System.currentTimeMillis();
                if (firstTick) {
                    nextShot = now; // 1º tiro imediato; evita "esvaziar o pente de uma vez"
                    firstTick = false;
                }
                if (now < drawUntil.getOrDefault(uuid, 0L) || now < reloadingUntil.getOrDefault(uuid, 0L)) {
                    endBurst(uuid);
                    return;
                }
                // Dispara todos os tiros já "agendados" até agora (catch-up p/ acertar a cadência no grid de ticks).
                while (now >= nextShot) {
                    int ammo = burstAmmo.getOrDefault(uuid, 0);
                    if (ammo <= 0) {
                        endBurst(uuid);
                        startReloadHeld(player, item);
                        return;
                    }
                    double spread = nextSpread(uuid, type, now, lastShot.get(uuid));
                    lastShot.put(uuid, now);
                    ammo--;
                    burstAmmo.put(uuid, ammo);
                    fireShot(player, type, spread);
                    showAmmo(player, type, ammo);
                    nextShot += cooldown;
                    if (ammo == 0) {
                        endBurst(uuid);
                        startReloadHeld(player, item);
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        autoTasks.put(uuid, task);
    }

    /** Encerra a rajada: cancela o loop e grava a munição restante de volta na arma. */
    private void endBurst(UUID uuid) {
        BukkitTask t = autoTasks.remove(uuid);
        if (t != null) {
            t.cancel();
        }
        Integer ammo = burstAmmo.remove(uuid);
        if (ammo == null) {
            return;
        }
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null) {
            return;
        }
        ItemStack gun = player.getInventory().getItemInMainHand();
        if (plugin.getItemRegistry().resolve(gun).filter(ci -> ci instanceof FirearmItem).isPresent()) {
            writeAmmo(gun, ammo);
            player.getInventory().setItemInMainHand(gun);
        }
    }

    private void startReloadHeld(Player player, FirearmItem item) {
        ItemStack gun = player.getInventory().getItemInMainHand();
        if (plugin.getItemRegistry().resolve(gun).filter(ci -> ci.id().equals(item.id())).isPresent()) {
            startReload(player, item, gun);
        }
    }

    // ---- Mira (ADS): Slowness sem partículas. Lunetas só dão mais zoom (amplifier maior). ----

    /** Alterna a mira: Slowness (aproxima o FOV) sem partículas. A luneta aproxima mais. */
    public void toggleAds(Player player) {
        PotionEffectType slowness = Registry.EFFECT.get(NamespacedKey.minecraft("slowness"));
        if (slowness == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (aiming.remove(uuid)) {
            player.removePotionEffect(slowness);
            player.playSound(player, Sound.ITEM_SPYGLASS_STOP_USING, 0.5f, 1.5f);
        } else {
            aiming.add(uuid);
            // O zoom vem da redução de velocidade (FOV): quanto mais lento, mais perto. Só o zoom
            // (sem partículas/ícone/overlay) — Slowness invisível. Com luneta: amplificador altíssimo
            // = jogador IMÓVEL + zoom no máximo (o FOV trava no piso da velocidade = ~2x). Sem luneta:
            // zoom leve e ainda podendo andar.
            int amplifier = scopeInHand(player) ? SCOPE_ADS_AMPLIFIER : 3;
            player.addPotionEffect(new PotionEffect(slowness, PotionEffect.INFINITE_DURATION, amplifier, false, false, false));
            player.playSound(player, Sound.ITEM_SPYGLASS_USE, 0.5f, 1.5f);
        }
    }

    /**
     * Limpa TODO o estado por-jogador (morte, saída, troca de mundo): cancela a rajada, tira a
     * mira e zera todos os timers. Também evita o crescimento sem-fim dos mapas por-UUID.
     */
    public void clearPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        endBurst(uuid);   // cancela a task de rajada + remove burstAmmo
        clearAds(player); // remove Slowness + aiming
        lastShot.remove(uuid);
        reloadingUntil.remove(uuid);
        recoilHeat.remove(uuid);
        meleeCooldown.remove(uuid);
        drawUntil.remove(uuid);
    }

    /** Zera o recuo/cadência acumulados (ao trocar de arma): o "calor" de uma não vaza pra outra. */
    public void resetSpread(Player player) {
        UUID uuid = player.getUniqueId();
        recoilHeat.remove(uuid);
        lastShot.remove(uuid);
    }

    /** Remove a mira (ao trocar de item, guardar a arma, etc.). */
    public void clearAds(Player player) {
        if (aiming.remove(player.getUniqueId())) {
            PotionEffectType slowness = Registry.EFFECT.get(NamespacedKey.minecraft("slowness"));
            if (slowness != null) {
                player.removePotionEffect(slowness);
            }
        }
    }

    private boolean scopeInHand(Player player) {
        return plugin.getItemRegistry().resolve(player.getInventory().getItemInMainHand())
                .filter(ci -> ci instanceof FirearmItem)
                .map(ci -> ((FirearmItem) ci).type())
                .map(t -> t != null && t.scope())
                .orElse(false);
    }

    /** Liga/desliga o componente de "comida" — usado só p/ detectar o segurar do botão direito no modo AUTO. */
    private void applyAutoFood(org.bukkit.inventory.meta.ItemMeta meta, boolean auto) {
        if (auto) {
            FoodComponent food = meta.getFood();
            food.setCanAlwaysEat(true);
            food.setEatSeconds(86400f); // nunca termina: dá pra segurar indefinidamente
            food.setNutrition(0);
            food.setSaturation(0);
            meta.setFood(food);
        } else {
            meta.setFood(null);
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
        int base = effectiveBase(gun, type); // respeita a skin (CMD da skin como base do saque)
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
        Integer skin = skinCmd(gun);
        if (skin != null) {
            return skin;
        }
        return plugin.getItemRegistry().resolve(gun)
                .filter(ci -> ci instanceof FirearmItem)
                .map(ci -> ((FirearmItem) ci).type())
                .map(t -> t == null ? null : t.customModelData())
                .orElse(null);
    }

    /** CMD base efetiva: a da skin, se houver; senão a do tipo da arma. */
    private int effectiveBase(ItemStack gun, FirearmType type) {
        Integer skin = skinCmd(gun);
        return skin != null ? skin : type.customModelData();
    }

    private Integer skinCmd(ItemStack gun) {
        if (gun == null || !gun.hasItemMeta()) {
            return null;
        }
        var pdc = gun.getItemMeta().getPersistentDataContainer();
        Integer cmd = pdc.get(ItemKeys.FIREARM_SKIN_CMD, PersistentDataType.INTEGER);
        if (cmd == null) {
            return null;
        }
        // Se a skin foi removida do gun-skins.yml, ignora o PDC órfão e volta ao modelo base.
        String skinId = pdc.get(ItemKeys.FIREARM_SKIN_ID, PersistentDataType.STRING);
        if (skinsConfig == null || skinsConfig.get(skinId) == null) {
            return null;
        }
        return cmd;
    }

    // ----- skins cosméticas (aplicadas à arma na mão) -----

    /** Aplica uma skin à arma na mão (mantém munição/atributos; só troca o modelo). */
    public void applySkin(Player player, String skinId) {
        ItemStack gun = player.getInventory().getItemInMainHand();
        FirearmItem item = plugin.getItemRegistry().resolve(gun)
                .filter(ci -> ci instanceof FirearmItem).map(ci -> (FirearmItem) ci).orElse(null);
        if (item == null) {
            player.sendMessage(Component.text("Segure a arma que você quer skinar.", NamedTextColor.RED));
            return;
        }
        FirearmSkin skin = skinsConfig != null ? skinsConfig.get(skinId) : null;
        if (skin == null) {
            player.sendMessage(Component.text("Skin '" + skinId + "' não existe.", NamedTextColor.RED));
            return;
        }
        if (!skin.firearmId().equalsIgnoreCase(item.id())) {
            player.sendMessage(Component.text("Essa skin é para a arma '" + skin.firearmId() + "'.",
                    NamedTextColor.RED));
            return;
        }
        gun.editMeta(meta -> {
            meta.setCustomModelData(skin.modelData());
            meta.getPersistentDataContainer().set(ItemKeys.FIREARM_SKIN_CMD, PersistentDataType.INTEGER, skin.modelData());
            meta.getPersistentDataContainer().set(ItemKeys.FIREARM_SKIN_ID, PersistentDataType.STRING, skin.id());
        });
        player.getInventory().setItemInMainHand(gun);
        player.sendMessage(Component.text("Skin aplicada: ", NamedTextColor.GREEN)
                .append(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(skin.display())));
    }

    /** Remove a skin da arma na mão, voltando ao modelo padrão. */
    public void removeSkin(Player player) {
        ItemStack gun = player.getInventory().getItemInMainHand();
        FirearmItem item = plugin.getItemRegistry().resolve(gun)
                .filter(ci -> ci instanceof FirearmItem).map(ci -> (FirearmItem) ci).orElse(null);
        if (item == null || item.type() == null) {
            player.sendMessage(Component.text("Segure a arma de onde remover a skin.", NamedTextColor.RED));
            return;
        }
        int base = item.type().customModelData();
        gun.editMeta(meta -> {
            meta.setCustomModelData(base);
            meta.getPersistentDataContainer().remove(ItemKeys.FIREARM_SKIN_CMD);
            meta.getPersistentDataContainer().remove(ItemKeys.FIREARM_SKIN_ID);
        });
        player.getInventory().setItemInMainHand(gun);
        player.sendMessage(Component.text("Skin removida — arma voltou ao padrão.", NamedTextColor.GRAY));
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
        // PDC ausente = arma malformada (não passou pelo build()): trata como vazia (recarrega),
        // em vez de "pente cheio" — senão viraria munição infinita no 1º equip.
        if (!gun.hasItemMeta()) {
            return 0;
        }
        Integer v = gun.getItemMeta().getPersistentDataContainer().get(ItemKeys.FIREARM_AMMO, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
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
            clearAds(player); // sem arma na mão -> tira a mira
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
            int ammo = burstAmmo.containsKey(uuid) ? burstAmmo.get(uuid) : readAmmo(main, type);
            player.sendActionBar(hudLine(uuid, type, ammo, main));
        }
    }

    private Component hudLine(UUID uuid, FirearmType type, int ammo, ItemStack gun) {
        Component line = ammoComponent(type, ammo);
        if (type.automatic()) {
            boolean auto = readFireMode(gun) == FireMode.AUTO;
            line = line.append(Component.text("  [" + (auto ? "AUTO" : "SEMI") + "]",
                    auto ? NamedTextColor.GOLD : NamedTextColor.AQUA));
        }
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
                    s.getInt("noise-radius", 40),
                    s.getBoolean("automatic", false),
                    s.getBoolean("scope", false)));
        }
    }
}
