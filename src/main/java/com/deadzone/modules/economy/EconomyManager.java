package com.deadzone.modules.economy;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Economia (scraps). Saldo vive no perfil (cache p/ online, DB p/ offline). Transferências entre
 * jogadores ONLINE são atômicas na main thread; admin e ranking cobrem jogadores offline via DB
 * (na thread FIFO do ProfileManager, sem corrida com o load no login).
 */
public class EconomyManager {

    private static final long CHARGE_TTL_MS = 5 * 60 * 1000L; // cobranças expiram em 5 min
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final DeadzonePlugin plugin;
    private final EconomyDao dao;

    private final Map<Integer, Charge> charges = new ConcurrentHashMap<>();
    private final AtomicInteger chargeSeq = new AtomicInteger();
    private volatile List<EconomyDao.TopEntry> topCache = List.of();

    public EconomyManager(DeadzonePlugin plugin, EconomyDao dao) {
        this.plugin = plugin;
        this.dao = dao;
    }

    public void enable() {
        refreshTop();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshTop, 200L, 1200L); // a cada 60s
    }

    public String format(long value) {
        return String.format(PT_BR, "%,d scraps", value);
    }

    // ----- saldo -----

    public long balanceOf(Player player) {
        PlayerProfile p = profileOf(player);
        return p != null ? p.getBalance() : 0L;
    }

    private PlayerProfile profileOf(Player player) {
        return plugin.getProfileManager().get(player.getUniqueId());
    }

    // ----- API para os sistemas de jogo (vendas, missões, upkeep, lojas) -----

    /** Concede scraps a um jogador online (venda de itens, recompensa de missão...). */
    public void reward(Player player, long amount) {
        if (amount <= 0) {
            return;
        }
        PlayerProfile p = profileOf(player);
        if (p != null) {
            p.addBalance(amount);
        }
    }

    /** Cobra scraps de um jogador online (compra, upkeep). @return false se não tiver saldo. */
    public boolean tryDebit(Player player, long amount) {
        if (amount <= 0) {
            return true;
        }
        PlayerProfile p = profileOf(player);
        if (p == null || p.getBalance() < amount) {
            return false;
        }
        p.addBalance(-amount);
        return true;
    }

    // ----- transferência (/pay /pix /pagar) — alvo deve estar ONLINE -----

    public void pay(Player from, String targetName, long amount) {
        if (amount <= 0) {
            error(from, "Valor inválido.");
            return;
        }
        if (from.getName().equalsIgnoreCase(targetName)) {
            error(from, "Você não pode pagar a si mesmo.");
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            error(from, "Esse jogador precisa estar online para receber.");
            return;
        }
        PlayerProfile fromP = profileOf(from);
        PlayerProfile toP = profileOf(target);
        if (fromP == null || toP == null) {
            error(from, "Perfil ainda carregando, tente de novo.");
            return;
        }
        if (fromP.getBalance() < amount) {
            error(from, "Saldo insuficiente — você tem " + format(fromP.getBalance()) + ".");
            return;
        }
        fromP.addBalance(-amount);
        toP.addBalance(amount);
        from.sendMessage(Component.text("Você pagou " + format(amount) + " para " + target.getName() + ".",
                NamedTextColor.GREEN));
        from.playSound(from, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        target.sendMessage(Component.text("Você recebeu " + format(amount) + " de " + from.getName() + ".",
                NamedTextColor.GREEN));
        target.playSound(target, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
    }

    // ----- cobrança (/cobrar) — clicável + confirmação -----

    public void createCharge(Player charger, String targetName, long amount) {
        if (amount <= 0) {
            error(charger, "Valor inválido.");
            return;
        }
        if (charger.getName().equalsIgnoreCase(targetName)) {
            error(charger, "Você não pode cobrar a si mesmo.");
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            error(charger, "Esse jogador precisa estar online para ser cobrado.");
            return;
        }
        int id = chargeSeq.incrementAndGet();
        charges.put(id, new Charge(id, charger.getUniqueId(), charger.getName(),
                target.getUniqueId(), amount, System.currentTimeMillis() + CHARGE_TTL_MS));
        charger.sendMessage(Component.text("Cobrança de " + format(amount) + " enviada para " + target.getName() + ".",
                NamedTextColor.GOLD));
        target.sendMessage(Component.text(charger.getName() + " está te cobrando " + format(amount) + ".",
                        NamedTextColor.GOLD)
                .append(Component.newline())
                .append(button("[✔ Pagar]", NamedTextColor.GREEN, "/cobrar pagar " + id, "Pagar a cobrança"))
                .append(Component.text("  "))
                .append(button("[✘ Recusar]", NamedTextColor.RED, "/cobrar recusar " + id, "Recusar a cobrança")));
        target.playSound(target, Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
    }

    /** Clicou em "Pagar": mostra a confirmação (passo extra, como pedido). */
    public void chargePayClicked(Player target, int id) {
        Charge charge = validCharge(target, id);
        if (charge == null) {
            return;
        }
        target.sendMessage(Component.text("Confirmar pagamento de " + format(charge.amount())
                        + " para " + charge.chargerName() + "?", NamedTextColor.YELLOW)
                .append(Component.newline())
                .append(button("[✔ Confirmar]", NamedTextColor.GREEN, "/cobrar confirmar " + id, "Confirmar"))
                .append(Component.text("  "))
                .append(button("[✘ Cancelar]", NamedTextColor.RED, "/cobrar recusar " + id, "Cancelar")));
    }

    /** Clicou em "Confirmar": executa o pagamento de fato. */
    public void chargeConfirmClicked(Player target, int id) {
        Charge charge = validCharge(target, id);
        if (charge == null) {
            return;
        }
        PlayerProfile targetP = profileOf(target);
        if (targetP == null) {
            error(target, "Perfil ainda carregando, tente de novo.");
            return;
        }
        if (targetP.getBalance() < charge.amount()) {
            error(target, "Saldo insuficiente — você tem " + format(targetP.getBalance()) + ".");
            return;
        }
        charges.remove(id);
        targetP.addBalance(-charge.amount());
        deposit(charge.charger(), charge.amount()); // o cobrador recebe (online: cache; offline: DB)
        target.sendMessage(Component.text("Você pagou " + format(charge.amount()) + " para "
                + charge.chargerName() + ".", NamedTextColor.GREEN));
        target.playSound(target, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        Player charger = Bukkit.getPlayer(charge.charger());
        if (charger != null) {
            charger.sendMessage(Component.text(target.getName() + " pagou sua cobrança de "
                    + format(charge.amount()) + ".", NamedTextColor.GREEN));
            charger.playSound(charger, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
        }
    }

    public void chargeDeclineClicked(Player actor, int id) {
        Charge charge = charges.remove(id);
        if (charge == null || !charge.target().equals(actor.getUniqueId())) {
            return;
        }
        actor.sendMessage(Component.text("Cobrança recusada.", NamedTextColor.GRAY));
        Player charger = Bukkit.getPlayer(charge.charger());
        if (charger != null) {
            charger.sendMessage(Component.text(actor.getName() + " recusou sua cobrança de "
                    + format(charge.amount()) + ".", NamedTextColor.RED));
        }
    }

    private Charge validCharge(Player target, int id) {
        Charge charge = charges.get(id);
        if (charge == null || !charge.target().equals(target.getUniqueId())) {
            error(target, "Cobrança não encontrada.");
            return null;
        }
        if (System.currentTimeMillis() > charge.expiresAt()) {
            charges.remove(id);
            error(target, "Essa cobrança expirou.");
            return null;
        }
        return charge;
    }

    // ----- admin (/eco) -----

    public void adminGive(CommandSender sender, String name, long amount) {
        if (amount <= 0) {
            error(sender, "Valor inválido.");
            return;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            profileOf(online).addBalance(amount);
            ok(sender, "Adicionado " + format(amount) + " a " + online.getName() + ".");
            return;
        }
        runDb(conn -> dao.addByName(conn, name, amount) > 0, found ->
                report(sender, found, "Adicionado " + format(amount) + " a " + name + " (offline)."));
    }

    public void adminTake(CommandSender sender, String name, long amount) {
        if (amount <= 0) {
            error(sender, "Valor inválido.");
            return;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            PlayerProfile p = profileOf(online);
            p.setBalance(p.getBalance() - amount); // setBalance trava o piso em 0
            ok(sender, "Removido " + format(amount) + " de " + online.getName() + ".");
            return;
        }
        runDb(conn -> {
            Long bal = dao.getBalanceByName(conn, name);
            if (bal == null) {
                return false;
            }
            dao.setByName(conn, name, Math.max(0L, bal - amount));
            return true;
        }, found -> report(sender, found, "Removido " + format(amount) + " de " + name + " (offline)."));
    }

    public void adminSet(CommandSender sender, String name, long value) {
        if (value < 0) {
            error(sender, "Valor inválido.");
            return;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            profileOf(online).setBalance(value);
            ok(sender, "Saldo de " + online.getName() + " definido para " + format(value) + ".");
            return;
        }
        runDb(conn -> dao.setByName(conn, name, value) > 0, found ->
                report(sender, found, "Saldo de " + name + " definido para " + format(value) + " (offline)."));
    }

    public void adminReset(CommandSender sender, String name) {
        adminSet(sender, name, 0L);
    }

    // ----- ponte Vault (síncrona): online via cache, offline via DB bloqueante (raro) -----

    public long vaultBalance(OfflinePlayer player) {
        Player online = player.isOnline() ? player.getPlayer() : null;
        if (online != null) {
            PlayerProfile p = profileOf(online);
            if (p != null) {
                return p.getBalance();
            }
        }
        try (Connection c = dao.database().getConnection()) {
            Long b = dao.getBalanceByUuid(c, player.getUniqueId());
            return b == null ? 0L : b;
        } catch (SQLException e) {
            plugin.getLogger().warning("Economia (vault saldo) falhou: " + e.getMessage());
            return 0L;
        }
    }

    public boolean vaultWithdraw(OfflinePlayer player, long amount) {
        if (amount <= 0) {
            return true;
        }
        Player online = player.isOnline() ? player.getPlayer() : null;
        if (online != null) {
            PlayerProfile p = profileOf(online);
            if (p == null || p.getBalance() < amount) {
                return false;
            }
            p.addBalance(-amount);
            return true;
        }
        try (Connection c = dao.database().getConnection()) {
            Long b = dao.getBalanceByUuid(c, player.getUniqueId());
            if (b == null || b < amount) {
                return false;
            }
            dao.setByUuid(c, player.getUniqueId(), b - amount);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("Economia (vault saque) falhou: " + e.getMessage());
            return false;
        }
    }

    public boolean vaultDeposit(OfflinePlayer player, long amount) {
        if (amount <= 0) {
            return true;
        }
        Player online = player.isOnline() ? player.getPlayer() : null;
        if (online != null) {
            PlayerProfile p = profileOf(online);
            if (p == null) {
                return false;
            }
            p.addBalance(amount);
            return true;
        }
        try (Connection c = dao.database().getConnection()) {
            dao.addByUuid(c, player.getUniqueId(), amount);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("Economia (vault deposito) falhou: " + e.getMessage());
            return false;
        }
    }

    // ----- baltop (top 10) -----

    public List<EconomyDao.TopEntry> top() {
        return topCache;
    }

    private void refreshTop() {
        runDbVoid(conn -> {
            List<EconomyDao.TopEntry> fresh = dao.top(conn, 10);
            plugin.getServer().getScheduler().runTask(plugin, () -> this.topCache = fresh);
        });
    }

    // ----- helpers -----

    /** Deposita para um jogador por UUID: online via cache, offline via DB. */
    private void deposit(UUID uuid, long amount) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && profileOf(online) != null) {
            profileOf(online).addBalance(amount);
            return;
        }
        runDbVoid(conn -> dao.addByUuid(conn, uuid, amount));
    }

    /** Roda uma operação de DB que retorna "encontrou?" na thread FIFO e entrega o resultado na main. */
    private void runDb(SqlBool op, Consumer<Boolean> onMain) {
        plugin.getProfileManager().submitDbTask(() -> {
            boolean found;
            try (Connection conn = dao.database().getConnection()) {
                found = op.run(conn);
            } catch (SQLException e) {
                found = false;
                plugin.getLogger().warning("Economia (DB) falhou: " + e.getMessage());
            }
            boolean f = found;
            plugin.getServer().getScheduler().runTask(plugin, () -> onMain.accept(f));
        });
    }

    private void runDbVoid(SqlVoid op) {
        plugin.getProfileManager().submitDbTask(() -> {
            try (Connection conn = dao.database().getConnection()) {
                op.run(conn);
            } catch (SQLException e) {
                plugin.getLogger().warning("Economia (DB) falhou: " + e.getMessage());
            }
        });
    }

    private void report(CommandSender sender, boolean found, String successMsg) {
        if (found) {
            ok(sender, successMsg);
        } else {
            error(sender, "Jogador não encontrado.");
        }
    }

    private Component button(String label, NamedTextColor color, String command, String hover) {
        return Component.text(label, color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    private void ok(CommandSender sender, String msg) {
        sender.sendMessage(Component.text(msg, NamedTextColor.GREEN));
    }

    private void error(CommandSender sender, String msg) {
        sender.sendMessage(Component.text(msg, NamedTextColor.RED));
    }

    @FunctionalInterface
    private interface SqlBool {
        boolean run(Connection c) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlVoid {
        void run(Connection c) throws SQLException;
    }

    private record Charge(int id, UUID charger, String chargerName, UUID target, long amount, long expiresAt) {
    }
}
