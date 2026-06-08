package com.deadzone.modules.classes.gui;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.gui.Menu;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.modules.classes.ClassManager;
import com.deadzone.modules.classes.SkillNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Árvore de habilidades (/skills). Mostra os nós da classe do jogador com estados visuais.
 */
public class SkillTreeMenu extends Menu {

    private final DeadzonePlugin plugin;

    public SkillTreeMenu(DeadzonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Component title() {
        return Component.text("Árvore de Habilidades", NamedTextColor.DARK_AQUA);
    }

    @Override
    public int size() {
        return 54;
    }

    @Override
    protected void build(Player viewer) {
        PlayerProfile profile = plugin.getProfileManager().get(viewer.getUniqueId());
        if (profile == null) {
            return;
        }

        for (SkillNode node : plugin.getClassManager().skills().nodesFor(profile.getPlayerClass())) {
            int slot = Math.max(0, Math.min(size() - 10, node.slot()));
            setItem(slot, nodeIcon(profile, node), e -> click((Player) e.getWhoClicked(), node));
        }

        ItemStack xpItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        xpItem.editMeta(meta -> meta.displayName(
                Component.text("XP disponível: " + profile.getXp(), NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)));
        setItem(49, xpItem);

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.empty()));
        fillEmpty(filler);
    }

    private ItemStack nodeIcon(PlayerProfile profile, SkillNode node) {
        boolean unlocked = profile.hasSkill(node.id());
        boolean prereqsOk = node.prerequisites().stream().allMatch(profile::hasSkill);
        boolean canAfford = profile.getXp() >= node.cost();

        ItemStack item = new ItemStack(node.icon());
        item.editMeta(meta -> {
            meta.displayName(Component.text(node.name(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : node.description()) {
                lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            if (unlocked) {
                lore.add(Component.text("✔ Desbloqueada", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                meta.setEnchantmentGlintOverride(true);
            } else if (!prereqsOk) {
                lore.add(Component.text("🔒 Requer: " + prereqNames(node), NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            } else if (!canAfford) {
                lore.add(Component.text("Custo: " + node.cost() + " XP (você tem " + profile.getXp() + ")", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Custo: " + node.cost() + " XP", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Clique para desbloquear", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        });
        return item;
    }

    private String prereqNames(SkillNode node) {
        List<String> names = new ArrayList<>();
        for (String id : node.prerequisites()) {
            SkillNode prereq = plugin.getClassManager().skills().get(id);
            names.add(prereq != null ? prereq.name() : id);
        }
        return String.join(", ", names);
    }

    private void click(Player player, SkillNode node) {
        PlayerProfile profile = plugin.getProfileManager().get(player.getUniqueId());
        if (profile == null) {
            return;
        }
        ClassManager.UnlockResult result = plugin.getClassManager().tryUnlock(profile, node);
        switch (result) {
            case SUCCESS -> {
                player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
                player.sendActionBar(Component.text("Desbloqueado: " + node.name(), NamedTextColor.GREEN));
                refresh(player);
            }
            case NO_XP -> {
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
                player.sendActionBar(Component.text("XP insuficiente.", NamedTextColor.RED));
            }
            case LOCKED -> player.sendActionBar(Component.text("Pré-requisitos não atendidos.", NamedTextColor.RED));
            case WRONG_CLASS -> player.sendActionBar(Component.text("Sua classe não pode usar esta skill.", NamedTextColor.RED));
            case ALREADY -> { /* nada */ }
        }
    }
}
