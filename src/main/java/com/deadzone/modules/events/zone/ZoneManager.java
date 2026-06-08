package com.deadzone.modules.events.zone;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.core.scheduler.TickService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Zonas tóxicas: dano por segundo a quem entra sem proteção (Máscara de Gás). */
public class ZoneManager {

    private static final Particle.DustOptions TOXIC =
            new Particle.DustOptions(Color.fromRGB(120, 200, 60), 1.2f);

    private final DeadzonePlugin plugin;
    private final ZoneConfig config;
    private final Map<UUID, Location[]> selections = new HashMap<>();

    public ZoneManager(DeadzonePlugin plugin, ZoneConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void enable(TickService tickService) {
        tickService.registerSecondHandler(this::tick);
    }

    public void reload() {
        config.load();
    }

    public ZoneConfig config() {
        return config;
    }

    public void tick(PlayerProfile profile) {
        Player player = plugin.getServer().getPlayer(profile.getUuid());
        if (player == null) {
            return;
        }
        GameMode mode = player.getGameMode();
        boolean canBeHarmed = mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;

        for (ToxicZone zone : config.zones()) {
            if (!player.getWorld().getName().equals(zone.world())) {
                continue;
            }
            renderBorder(player, zone); // contorno visível em qualquer modo de jogo
            renderHaze(player, zone);   // névoa tóxica de aviso

            if (!zone.contains(player.getLocation())) {
                continue;
            }
            if (isProtected(player, zone)) {
                player.sendActionBar(Component.text("☢ Zona tóxica — protegido.", NamedTextColor.GREEN));
            } else if (canBeHarmed) {
                player.damage(zone.damagePerSecond());
                applyEffects(player, zone.effects());
                player.sendActionBar(Component.text("☢ Zona tóxica! Equipe a Máscara de Gás.", NamedTextColor.RED));
                player.playSound(player, Sound.BLOCK_FIRE_AMBIENT, 0.5f, 0.7f);
            }
        }
    }

    /** Desenha as 12 arestas da zona com partículas, visível só para o jogador (se estiver perto). */
    private void renderBorder(Player player, ToxicZone z) {
        double x1 = z.minX(), y1 = z.minY(), z1 = z.minZ();
        double x2 = z.maxX() + 1, y2 = z.maxY() + 1, z2 = z.maxZ() + 1;
        double cx = (x1 + x2) / 2, cy = (y1 + y2) / 2, cz = (z1 + z2) / 2;
        if (player.getLocation().distanceSquared(new Location(player.getWorld(), cx, cy, cz)) > 56 * 56) {
            return;
        }
        double s = 2.0;
        // arestas ao longo de X
        line(player, x1, y1, z1, x2, y1, z1, s);
        line(player, x1, y1, z2, x2, y1, z2, s);
        line(player, x1, y2, z1, x2, y2, z1, s);
        line(player, x1, y2, z2, x2, y2, z2, s);
        // arestas ao longo de Z
        line(player, x1, y1, z1, x1, y1, z2, s);
        line(player, x2, y1, z1, x2, y1, z2, s);
        line(player, x1, y2, z1, x1, y2, z2, s);
        line(player, x2, y2, z1, x2, y2, z2, s);
        // arestas ao longo de Y
        line(player, x1, y1, z1, x1, y2, z1, s);
        line(player, x2, y1, z1, x2, y2, z1, s);
        line(player, x1, y1, z2, x1, y2, z2, s);
        line(player, x2, y1, z2, x2, y2, z2, s);
    }

    /** Névoa tóxica: partículas espalhadas dentro da zona sinalizando o perigo. */
    private void renderHaze(Player player, ToxicZone z) {
        double cx = (z.minX() + z.maxX() + 1) / 2.0;
        double cy = (z.minY() + z.maxY() + 1) / 2.0;
        double cz = (z.minZ() + z.maxZ() + 1) / 2.0;
        if (player.getLocation().distanceSquared(new Location(player.getWorld(), cx, cy, cz)) > 56 * 56) {
            return;
        }
        var rng = java.util.concurrent.ThreadLocalRandom.current();
        int points = 24;
        for (int i = 0; i < points; i++) {
            double x = z.minX() + rng.nextDouble() * (z.maxX() - z.minX() + 1);
            double y = z.minY() + rng.nextDouble() * (z.maxY() - z.minY() + 1);
            double zc = z.minZ() + rng.nextDouble() * (z.maxZ() - z.minZ() + 1);
            Location loc = new Location(player.getWorld(), x, y, zc);
            player.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, TOXIC);
            if (rng.nextDouble() < 0.25) {
                player.spawnParticle(Particle.SNEEZE, loc, 1, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }

    private void line(Player player, double x1, double y1, double z1, double x2, double y2, double z2, double step) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) (len / step));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Location loc = new Location(player.getWorld(), x1 + dx * t, y1 + dy * t, z1 + dz * t);
            player.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, TOXIC);
        }
    }

    private boolean isProtected(Player player, ToxicZone zone) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null) {
            return false;
        }
        return plugin.getItemRegistry().resolve(helmet)
                .map(ci -> ci.id().equals(zone.protectionItemId())).orElse(false);
    }

    private void applyEffects(Player player, List<String> effects) {
        for (String id : effects) {
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(id.toLowerCase()));
            if (type != null) {
                player.addPotionEffect(new PotionEffect(type, 60, 0, true, true, true));
            }
        }
    }

    public void setPos(Player player, int index) {
        Location[] sel = selections.computeIfAbsent(player.getUniqueId(), k -> new Location[2]);
        sel[index] = player.getLocation();
    }

    public boolean createZone(Player player, String name, double dps) {
        Location[] sel = selections.get(player.getUniqueId());
        if (sel == null || sel[0] == null || sel[1] == null) {
            return false;
        }
        Location a = sel[0];
        Location b = sel[1];
        ToxicZone zone = new ToxicZone(
                name,
                a.getWorld().getName(),
                Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()),
                dps,
                "gas_mask",
                List.of("poison", "nausea"));
        config.add(zone);
        return true;
    }

    public boolean removeZone(String name) {
        return config.remove(name);
    }
}
