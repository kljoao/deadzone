package com.deadzone.modules.medicine.bandage;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.modules.medicine.item.DefinedItem;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Canalização da bandagem: alguns segundos imóvel para estancar o sangramento, interrompível.
 * Como o Bukkit não expõe "soltar o botão direito" para itens comuns, a canalização começa no clique.
 */
public class BandageService {

    private final DeadzonePlugin plugin;
    private final Map<UUID, Channel> channels = new HashMap<>();

    public BandageService(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isChanneling(UUID uuid) {
        return channels.containsKey(uuid);
    }

    /** Inicia a canalização (assumindo que já foi validado que há sangramento e cooldown ok). */
    public void start(Player player, DefinedItem item) {
        UUID uuid = player.getUniqueId();
        if (channels.containsKey(uuid)) {
            return;
        }
        int cast = Math.max(1, item.definition().useInt("cast-seconds", 5));
        int totalTicks = cast * 20;

        float originalWalkSpeed = player.getWalkSpeed();
        player.setWalkSpeed(0f);     // imobiliza o deslocamento
        player.setSprinting(false);  // mata o momentum de corrida
        applyImmobilize(player, totalTicks + 20); // slow supremo + sem pulo

        BossBar bar = BossBar.bossBar(
                Component.text("Aplicando bandagem...", NamedTextColor.WHITE),
                0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);
        player.playSound(player, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.2f);

        Channel channel = new Channel(originalWalkSpeed, bar, totalTicks, item);
        channel.task = plugin.getServer().getScheduler().runTaskTimer(
                plugin, () -> tick(player, channel), 1L, 1L);
        channels.put(uuid, channel);
    }

    private void tick(Player player, Channel channel) {
        channel.elapsed++;
        channel.bar.progress(Math.min(1f, (float) channel.elapsed / channel.totalTicks));
        if (channel.elapsed % 4 == 0) {
            player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0),
                    4, 0.25, 0.4, 0.25, 0, new Particle.DustOptions(Color.fromRGB(200, 30, 30), 1.0f));
        }
        if (channel.elapsed >= channel.totalTicks) {
            complete(player, channel);
        }
    }

    private void complete(Player player, Channel channel) {
        PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
        if (profile != null) {
            plugin.getMedicineManager().bleeding().stop(profile);
            if (!channel.item.id().equals("bandagem_esterilizada")) {
                plugin.getMedicineManager().bleeding().rollWoundInfection(player, profile);
            }
        }
        consumeMainHand(player, channel.item.id());
        plugin.getMedicineManager().applyCooldown(player, channel.item.id(),
                channel.item.definition().cooldownSeconds());
        player.playSound(player, Sound.BLOCK_WOOL_PLACE, 1.0f, 1.0f);
        player.sendActionBar(Component.text("Sangramento estancado.", NamedTextColor.GREEN));
        finish(player, channel);
    }

    /** Cancela a canalização em andamento (interrupção). */
    public void cancel(Player player) {
        Channel channel = channels.get(player.getUniqueId());
        if (channel == null) {
            return;
        }
        finish(player, channel);
        player.sendActionBar(Component.text("Bandagem interrompida.", NamedTextColor.RED));
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
    }

    /** Limpeza silenciosa (saída do jogador). */
    public void quietCleanup(Player player) {
        Channel channel = channels.remove(player.getUniqueId());
        if (channel == null) {
            return;
        }
        if (channel.task != null) {
            channel.task.cancel();
        }
        player.setWalkSpeed(channel.originalWalkSpeed);
        clearImmobilize(player);
        player.hideBossBar(channel.bar);
    }

    private void finish(Player player, Channel channel) {
        player.setWalkSpeed(channel.originalWalkSpeed);
        clearImmobilize(player);
        player.hideBossBar(channel.bar);
        if (channel.task != null) {
            channel.task.cancel();
        }
        channels.remove(player.getUniqueId());
    }

    private void applyImmobilize(Player player, int ticks) {
        addEffect(player, "slowness", 255, ticks);  // slow supremo: não anda
        addEffect(player, "jump_boost", 128, ticks); // amplifier 128 impede o pulo
    }

    private void clearImmobilize(Player player) {
        removeEffect(player, "slowness");
        removeEffect(player, "jump_boost");
    }

    private void addEffect(Player player, String id, int amplifier, int ticks) {
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(id));
        if (type != null) {
            player.addPotionEffect(new PotionEffect(type, ticks, amplifier, true, false, false));
        }
    }

    private void removeEffect(Player player, String id) {
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(id));
        if (type != null) {
            player.removePotionEffect(type);
        }
    }

    private void consumeMainHand(Player player, String itemId) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean isItem = plugin.getItemRegistry().resolve(hand)
                .map(ci -> ci.id().equals(itemId)).orElse(false);
        if (isItem) {
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    /** Estado de uma canalização ativa. */
    private static final class Channel {
        final float originalWalkSpeed;
        final BossBar bar;
        final int totalTicks;
        final DefinedItem item;
        int elapsed;
        BukkitTask task;

        Channel(float originalWalkSpeed, BossBar bar, int totalTicks, DefinedItem item) {
            this.originalWalkSpeed = originalWalkSpeed;
            this.bar = bar;
            this.totalTicks = totalTicks;
            this.item = item;
        }
    }
}
