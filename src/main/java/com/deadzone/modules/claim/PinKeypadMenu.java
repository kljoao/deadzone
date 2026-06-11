package com.deadzone.modules.claim;

import com.deadzone.core.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Function;

/** Teclado 0–9 para definir (2x) ou digitar a senha de um baú. */
public class PinKeypadMenu extends Menu {

    public enum Mode { SET, UNLOCK }

    private final Mode mode;
    private final Function<String, Boolean> onSubmit; // SET: tranca e retorna true; UNLOCK: confere o PIN
    private final Runnable onCancel;

    private final StringBuilder entry = new StringBuilder();
    private String firstPin;
    private boolean confirming;
    private boolean completed;

    public PinKeypadMenu(Mode mode, Function<String, Boolean> onSubmit, Runnable onCancel) {
        this.mode = mode;
        this.onSubmit = onSubmit;
        this.onCancel = onCancel;
    }

    @Override
    public Component title() {
        return Component.text("Senha do Baú", NamedTextColor.DARK_GREEN);
    }

    @Override
    public int size() {
        return 45;
    }

    @Override
    protected void build(Player viewer) {
        setItem(4, display());
        int[] slots = {10, 11, 12, 19, 20, 21, 28, 29, 30};
        for (int d = 1; d <= 9; d++) {
            int digit = d;
            setItem(slots[d - 1], digitItem(d), e -> press(viewer, digit));
        }
        setItem(37, digitItem(0), e -> press(viewer, 0));
        setItem(39, ClaimIcons.item(Material.BARRIER,
                        Component.text("Apagar", NamedTextColor.GOLD),
                        Component.text("Apaga o último dígito", NamedTextColor.GRAY)),
                e -> {
                    if (entry.length() > 0) {
                        entry.setLength(entry.length() - 1);
                        viewer.playSound(viewer, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 0.8f);
                        refresh(viewer);
                    }
                });
        setItem(41, ClaimIcons.item(Material.LIME_CONCRETE,
                        Component.text("Confirmar", NamedTextColor.GREEN),
                        Component.text("Confirma os 4 dígitos", NamedTextColor.GRAY)),
                e -> confirm(viewer));
        setItem(43, ClaimIcons.item(Material.RED_CONCRETE,
                        Component.text("Cancelar", NamedTextColor.RED),
                        Component.text("Fecha sem salvar", NamedTextColor.GRAY)),
                e -> {
                    viewer.playSound(viewer, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                    viewer.closeInventory();
                });
        fillEmpty(ClaimIcons.filler());
    }

    private void press(Player viewer, int digit) {
        if (entry.length() >= 4) {
            return;
        }
        entry.append(digit);
        viewer.playSound(viewer, Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        refresh(viewer);
    }

    private void confirm(Player viewer) {
        if (entry.length() != 4) {
            viewer.playSound(viewer, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
            viewer.sendActionBar(Component.text("Digite os 4 dígitos antes de confirmar.", NamedTextColor.RED));
            return;
        }
        String pin = entry.toString();
        if (mode == Mode.SET && !confirming) {
            // Passo 1 concluído: agora repetir.
            firstPin = pin;
            confirming = true;
            entry.setLength(0);
            viewer.playSound(viewer, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
            viewer.sendActionBar(Component.text("✔ Senha digitada! Agora digite de novo para confirmar.",
                    NamedTextColor.GREEN));
            refresh(viewer);
            return;
        }
        if (mode == Mode.SET) {
            if (!pin.equals(firstPin)) {
                firstPin = null;
                confirming = false;
                entry.setLength(0);
                viewer.playSound(viewer, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                viewer.sendActionBar(Component.text("As senhas não bateram — comece de novo (Passo 1).",
                        NamedTextColor.RED));
                refresh(viewer);
                return;
            }
            completed = true;
            viewer.playSound(viewer, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
            onSubmit.apply(pin);
            viewer.closeInventory();
            return;
        }
        // UNLOCK
        if (onSubmit.apply(pin)) {
            completed = true;
            viewer.playSound(viewer, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
            viewer.closeInventory();
        } else {
            entry.setLength(0);
            viewer.playSound(viewer, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            viewer.sendActionBar(Component.text("Senha incorreta.", NamedTextColor.RED));
            refresh(viewer);
        }
    }

    private ItemStack digitItem(int d) {
        return ClaimIcons.item(Material.PAPER, Component.text(String.valueOf(d), NamedTextColor.YELLOW));
    }

    private ItemStack display() {
        String mask = ("● ".repeat(entry.length()) + "○ ".repeat(4 - entry.length())).trim();
        if (mode == Mode.UNLOCK) {
            return ClaimIcons.item(Material.NAME_TAG,
                    Component.text("Digite a senha do baú", NamedTextColor.AQUA),
                    Component.text(mask, NamedTextColor.WHITE),
                    Component.empty(),
                    Component.text("Clique nos números e em Confirmar.", NamedTextColor.GRAY));
        }
        if (!confirming) {
            return ClaimIcons.item(Material.NAME_TAG,
                    Component.text("Passo 1 de 2: criar a senha", NamedTextColor.AQUA),
                    Component.text(mask, NamedTextColor.WHITE),
                    Component.empty(),
                    Component.text("Digite 4 dígitos e clique em", NamedTextColor.GRAY),
                    Component.text("Confirmar.", NamedTextColor.GRAY));
        }
        return ClaimIcons.item(Material.NAME_TAG,
                Component.text("Passo 2 de 2: repetir a senha", NamedTextColor.GREEN),
                Component.text(mask, NamedTextColor.WHITE),
                Component.empty(),
                Component.text("Digite os MESMOS 4 dígitos e", NamedTextColor.GRAY),
                Component.text("clique em Confirmar.", NamedTextColor.GRAY));
    }

    public boolean isCompleted() {
        return completed;
    }

    /** Chamado quando o menu fecha; se não concluiu, dispara o cancelamento (ex.: devolver o baú). */
    public void handleClose() {
        if (!completed) {
            onCancel.run();
        }
    }
}
