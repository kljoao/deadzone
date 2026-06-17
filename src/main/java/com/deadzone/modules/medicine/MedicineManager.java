package com.deadzone.modules.medicine;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.deadzone.core.item.CustomItem;
import com.deadzone.core.item.ItemRegistry;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.core.scheduler.TickService;
import com.deadzone.modules.medicine.bandage.BandageListener;
import com.deadzone.modules.medicine.bandage.BandageService;
import com.deadzone.modules.medicine.bleeding.BleedingConfig;
import com.deadzone.modules.medicine.bleeding.BleedingManager;
import com.deadzone.modules.medicine.brokenleg.BrokenLegManager;
import com.deadzone.modules.medicine.item.Analgesico;
import com.deadzone.modules.medicine.item.Antidoto;
import com.deadzone.modules.medicine.item.Bandagem;
import com.deadzone.modules.medicine.item.DefinedItem;
import com.deadzone.modules.medicine.item.Desfibrilador;
import com.deadzone.modules.medicine.item.ItemDefinition;
import com.deadzone.modules.medicine.item.ItemsConfig;
import com.deadzone.modules.medicine.item.KitPrimeirosSocorros;
import com.deadzone.modules.medicine.item.SeringaAdrenalina;
import com.deadzone.modules.medicine.item.Tala;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Orquestra o módulo de medicina: sangramento, itens customizados, cooldowns e a bancada. */
public class MedicineManager {

    public enum CraftResult {SUCCESS, MISSING_INGREDIENTS, NO_SKILL}

    private final DeadzonePlugin plugin;
    private final ItemsConfig itemsConfig;
    private final BleedingConfig bleedingConfig;
    private final BleedingManager bleedingManager;
    private final BandageService bandageService;
    private final BrokenLegManager brokenLegManager;

    // cooldowns: uuid -> (itemId -> expiraEm millis)
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public MedicineManager(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.itemsConfig = new ItemsConfig(plugin, configManager);
        this.bleedingConfig = new BleedingConfig(configManager);
        this.bleedingManager = new BleedingManager(plugin, bleedingConfig);
        this.bandageService = new BandageService(plugin);
        this.brokenLegManager = new BrokenLegManager(plugin, configManager);
    }

    public void enable(TickService tickService) {
        bleedingManager.enable(tickService);
        plugin.getServer().getPluginManager().registerEvents(new BandageListener(bandageService), plugin);
        brokenLegManager.enable(tickService);
        registerItems();
    }

    public void reload() {
        itemsConfig.load();
        bleedingConfig.load();
        brokenLegManager.reload();
        registerItems();
    }

    public BleedingManager bleeding() {
        return bleedingManager;
    }

    public BandageService bandage() {
        return bandageService;
    }

    public BrokenLegManager brokenLeg() {
        return brokenLegManager;
    }

    public ItemsConfig items() {
        return itemsConfig;
    }

    private void registerItems() {
        ItemRegistry registry = plugin.getItemRegistry();
        for (ItemDefinition def : itemsConfig.all()) {
            CustomItem item = switch (def.id()) {
                case "bandagem", "bandagem_esterilizada" -> new Bandagem(plugin, def);
                case "analgesico" -> new Analgesico(plugin, def);
                case "antidoto" -> new Antidoto(plugin, def);
                case "seringa_adrenalina" -> new SeringaAdrenalina(plugin, def);
                case "kit_primeiros_socorros" -> new KitPrimeirosSocorros(plugin, def);
                case "desfibrilador" -> new Desfibrilador(plugin, def);
                case "tala" -> new Tala(plugin, def);
                default -> new DefinedItem(plugin, def) {
                }; // item sem comportamento especial
            };
            registry.register(item);
        }
    }

    public long cooldownRemaining(Player player, String itemId) {
        Map<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) {
            return 0;
        }
        Long until = map.get(itemId);
        if (until == null) {
            return 0;
        }
        long remainingMs = until - System.currentTimeMillis();
        return remainingMs <= 0 ? 0 : (remainingMs + 999) / 1000; // arredonda p/ cima em segundos
    }

    public void applyCooldown(Player player, String itemId, int seconds) {
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(itemId, System.currentTimeMillis() + seconds * 1000L);
    }

    /** Remove os cooldowns de um jogador ao sair (senão o mapa cresce sem limite). */
    public void removeCooldowns(UUID uuid) {
        cooldowns.remove(uuid);
    }

    /** Itens que este jogador pode fabricar agora (têm receita e a skill exigida, se houver). */
    public List<ItemDefinition> craftableFor(Player player) {
        PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
        List<ItemDefinition> out = new ArrayList<>();
        for (ItemDefinition def : itemsConfig.all()) {
            if (!def.hasRecipe()) {
                continue;
            }
            if (def.requiredSkill() != null && (profile == null || !profile.hasSkill(def.requiredSkill()))) {
                continue;
            }
            out.add(def);
        }
        return out;
    }

    /** Itens (com receita) de uma categoria/aba. Inclui os bloqueados — a bancada os mostra em cinza. */
    public List<ItemDefinition> byCategory(String category) {
        List<ItemDefinition> out = new ArrayList<>();
        for (ItemDefinition def : itemsConfig.all()) {
            if (def.hasRecipe() && def.category().equalsIgnoreCase(category)) {
                out.add(def);
            }
        }
        return out;
    }

    /** O jogador tem a skill exigida para fabricar este item? */
    public boolean hasSkillFor(Player player, ItemDefinition def) {
        if (def.requiredSkill() == null) {
            return true;
        }
        PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
        return profile != null && profile.hasSkill(def.requiredSkill());
    }

    public CraftResult craft(Player player, ItemDefinition def) {
        if (!def.hasRecipe()) {
            return CraftResult.MISSING_INGREDIENTS;
        }
        PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
        if (def.requiredSkill() != null && (profile == null || !profile.hasSkill(def.requiredSkill()))) {
            return CraftResult.NO_SKILL;
        }
        // Resolve o produto ANTES de consumir ingredientes (se não estiver registrado, não gasta nada).
        CustomItem product = plugin.getItemRegistry().get(def.id());
        if (product == null) {
            plugin.getLogger().warning("Item '" + def.id() + "' sem registro — receita ignorada.");
            return CraftResult.MISSING_INGREDIENTS;
        }
        for (Map.Entry<String, Integer> entry : def.recipe().entrySet()) {
            if (countIngredient(player, entry.getKey()) < entry.getValue()) {
                return CraftResult.MISSING_INGREDIENTS;
            }
        }
        for (Map.Entry<String, Integer> entry : def.recipe().entrySet()) {
            removeMatching(player, matcher(entry.getKey()), entry.getValue());
        }
        ItemStack result = product.build();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(result);
        leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        return CraftResult.SUCCESS;
    }

    /** Conta quantas unidades de um ingrediente (item custom OU material vanilla) o jogador tem. */
    public int countIngredient(Player player, String key) {
        return countMatching(player, matcher(key));
    }

    private Predicate<ItemStack> matcher(String key) {
        CustomItem custom = plugin.getItemRegistry().get(key);
        if (custom != null) {
            return stack -> plugin.getItemRegistry().resolve(stack)
                    .map(ci -> ci.id().equals(key)).orElse(false);
        }
        Material material = Material.matchMaterial(key);
        return stack -> material != null && stack.getType() == material
                && plugin.getItemRegistry().resolve(stack).isEmpty();
    }

    private int countMatching(Player player, Predicate<ItemStack> match) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && match.test(stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void removeMatching(Player player, Predicate<ItemStack> match, int qty) {
        int remaining = qty;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || !match.test(stack)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
        }
    }
}
