package com.deadzone.modules.economy;

import com.deadzone.core.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Acesso ao saldo no banco para jogadores OFFLINE e para o ranking (baltop).
 * Jogadores online mexem no saldo via cache do perfil; estes métodos cobrem o resto.
 * Todos recebem a Connection (a chamada roda na thread FIFO de DB do ProfileManager).
 */
public class EconomyDao {

    private final Database database;

    public EconomyDao(Database database) {
        this.database = database;
    }

    public Database database() {
        return database;
    }

    /** Saldo de um jogador pelo nome (null se nunca entrou no servidor). */
    public Long getBalanceByName(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM players WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /** Soma delta ao saldo pelo nome. @return linhas afetadas (0 = jogador não existe). */
    public int addByName(Connection c, String name, long delta) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE players SET balance = balance + ? WHERE LOWER(name) = LOWER(?)")) {
            ps.setLong(1, delta);
            ps.setString(2, name);
            return ps.executeUpdate();
        }
    }

    /** Define o saldo pelo nome (nunca negativo). @return linhas afetadas. */
    public int setByName(Connection c, String name, long value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE players SET balance = ? WHERE LOWER(name) = LOWER(?)")) {
            ps.setLong(1, Math.max(0L, value));
            ps.setString(2, name);
            return ps.executeUpdate();
        }
    }

    /** Saldo pelo UUID (null se não existir). */
    public Long getBalanceByUuid(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /** Define o saldo pelo UUID (nunca negativo). @return linhas afetadas. */
    public int setByUuid(Connection c, UUID uuid, long value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE players SET balance = ? WHERE uuid = ?")) {
            ps.setLong(1, Math.max(0L, value));
            ps.setString(2, uuid.toString());
            return ps.executeUpdate();
        }
    }

    /** Soma delta ao saldo pelo UUID (usado ao depositar p/ um cobrador offline). */
    public void addByUuid(Connection c, UUID uuid, long delta) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE players SET balance = balance + ? WHERE uuid = ?")) {
            ps.setLong(1, delta);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    /** Os mais ricos (inclui offline). */
    public List<TopEntry> top(Connection c, int limit) throws SQLException {
        List<TopEntry> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT name, balance FROM players ORDER BY balance DESC, name ASC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String n = rs.getString("name");
                    out.add(new TopEntry(n != null ? n : "?", rs.getLong("balance")));
                }
            }
        }
        return out;
    }

    public record TopEntry(String name, long balance) {
    }
}
