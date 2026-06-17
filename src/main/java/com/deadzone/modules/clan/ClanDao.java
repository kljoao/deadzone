package com.deadzone.modules.clan;

import com.deadzone.core.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistência dos clans e dos seus integrantes. Métodos recebem a Connection; o manager a gerencia. */
public class ClanDao {

    private final Database database;

    public ClanDao(Database database) {
        this.database = database;
    }

    public Database database() {
        return database;
    }

    /** Carrega todos os clans (com integrantes) do banco. Usado no boot. */
    public List<Clan> loadAll(Connection c) throws SQLException {
        Map<String, Clan> byId = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM clans");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Clan clan = new Clan(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("tag"),
                        rs.getString("color"),
                        UUID.fromString(rs.getString("leader_uuid")),
                        rs.getLong("created_at"));
                clan.setBank(rs.getLong("bank"));
                clan.setLevel(rs.getLong("level"));
                clan.setXp(rs.getLong("xp"));
                clan.setFriendlyFire(rs.getInt("friendly_fire") != 0);
                clan.setGlow(rs.getInt("glow") != 0);
                clan.setSymbol(rs.getInt("symbol") != 0);
                byId.put(clan.id(), clan);
            }
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM clan_members");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Clan clan = byId.get(rs.getString("clan_id"));
                if (clan == null) {
                    continue;
                }
                clan.putMember(new ClanMember(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("name"),
                        ClanRole.fromString(rs.getString("role")),
                        rs.getLong("joined_at")));
            }
        }
        return new ArrayList<>(byId.values());
    }

    public void insertClan(Connection c, Clan clan) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO clans (id, name, tag, color, leader_uuid, created_at, bank, level, xp,"
                        + " friendly_fire, glow, symbol) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, clan.id());
            ps.setString(2, clan.name());
            ps.setString(3, clan.tag());
            ps.setString(4, clan.color());
            ps.setString(5, clan.leader().toString());
            ps.setLong(6, clan.createdAt());
            ps.setLong(7, clan.bank());
            ps.setLong(8, clan.level());
            ps.setLong(9, clan.xp());
            ps.setInt(10, clan.friendlyFire() ? 1 : 0);
            ps.setInt(11, clan.glow() ? 1 : 0);
            ps.setInt(12, clan.symbol() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public void updateClan(Connection c, Clan clan) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE clans SET name = ?, tag = ?, color = ?, leader_uuid = ?, bank = ?, level = ?,"
                        + " xp = ?, friendly_fire = ?, glow = ?, symbol = ? WHERE id = ?")) {
            ps.setString(1, clan.name());
            ps.setString(2, clan.tag());
            ps.setString(3, clan.color());
            ps.setString(4, clan.leader().toString());
            ps.setLong(5, clan.bank());
            ps.setLong(6, clan.level());
            ps.setLong(7, clan.xp());
            ps.setInt(8, clan.friendlyFire() ? 1 : 0);
            ps.setInt(9, clan.glow() ? 1 : 0);
            ps.setInt(10, clan.symbol() ? 1 : 0);
            ps.setString(11, clan.id());
            ps.executeUpdate();
        }
    }

    public void deleteClan(Connection c, String clanId) throws SQLException {
        // Apaga membros primeiro (cobre bancos sem ON DELETE CASCADE efetivo).
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM clan_members WHERE clan_id = ?")) {
            ps.setString(1, clanId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM clans WHERE id = ?")) {
            ps.setString(1, clanId);
            ps.executeUpdate();
        }
    }

    public void upsertMember(Connection c, String clanId, ClanMember m) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO clan_members (uuid, clan_id, name, role, joined_at) VALUES (?, ?, ?, ?, ?)"
                        + " ON CONFLICT(uuid) DO UPDATE SET clan_id = excluded.clan_id,"
                        + " name = excluded.name, role = excluded.role")) {
            ps.setString(1, m.uuid().toString());
            ps.setString(2, clanId);
            ps.setString(3, m.lastKnownName());
            ps.setString(4, m.role().name());
            ps.setLong(5, m.joinedAt());
            ps.executeUpdate();
        }
    }

    public void deleteMember(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM clan_members WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }
}
