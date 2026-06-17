package com.deadzone.modules.economy;

import com.deadzone.DeadzonePlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.List;

/**
 * Ponte Vault: expõe os scraps (saldo no perfil) para o ecossistema (placeholders, lojas, etc.).
 * Moeda inteira (fractionalDigits = 0); sem suporte a bancos.
 */
public class VaultEconomyProvider implements Economy {

    private final EconomyManager economy;

    public VaultEconomyProvider(DeadzonePlugin plugin) {
        this.economy = plugin.getEconomyManager();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "Deadzone Scraps";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 0;
    }

    @Override
    public String format(double amount) {
        return economy.format((long) amount);
    }

    @Override
    public String currencyNamePlural() {
        return "scraps";
    }

    @Override
    public String currencyNameSingular() {
        return "scrap";
    }

    // ----- contas (todo jogador tem; a linha em players nasce no 1º join) -----

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAccount(String playerName) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAccount(String playerName, String worldName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean createPlayerAccount(String playerName) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean createPlayerAccount(String playerName, String worldName) {
        return true;
    }

    // ----- saldo -----

    @Override
    public double getBalance(OfflinePlayer player) {
        return economy.vaultBalance(player);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return economy.vaultBalance(player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public double getBalance(String playerName) {
        return economy.vaultBalance(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    @SuppressWarnings("deprecation")
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return economy.vaultBalance(player) >= (long) amount;
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean has(String playerName, double amount) {
        return has(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    // ----- saque / depósito -----

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        long amt = (long) amount;
        if (amt < 0) {
            return fail("Valor inválido.");
        }
        boolean ok = economy.vaultWithdraw(player, amt);
        return ok ? success(amt, economy.vaultBalance(player)) : fail("Saldo insuficiente.");
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    @SuppressWarnings("deprecation")
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    @SuppressWarnings("deprecation")
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        long amt = (long) amount;
        if (amt < 0) {
            return fail("Valor inválido.");
        }
        boolean ok = economy.vaultDeposit(player, amt);
        return ok ? success(amt, economy.vaultBalance(player)) : fail("Falha ao depositar.");
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    @SuppressWarnings("deprecation")
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    @SuppressWarnings("deprecation")
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    // ----- bancos: não suportado -----

    private EconomyResponse noBank() {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Sem suporte a bancos.");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return noBank();
    }

    @Override
    @SuppressWarnings("deprecation")
    public EconomyResponse createBank(String name, String player) {
        return noBank();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return noBank();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return noBank();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return noBank();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return noBank();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return noBank();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return noBank();
    }

    @Override
    @SuppressWarnings("deprecation")
    public EconomyResponse isBankOwner(String name, String playerName) {
        return noBank();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return noBank();
    }

    @Override
    @SuppressWarnings("deprecation")
    public EconomyResponse isBankMember(String name, String playerName) {
        return noBank();
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    private EconomyResponse success(double amount, double balance) {
        return new EconomyResponse(amount, balance, ResponseType.SUCCESS, null);
    }

    private EconomyResponse fail(String msg) {
        return new EconomyResponse(0, 0, ResponseType.FAILURE, msg);
    }
}
