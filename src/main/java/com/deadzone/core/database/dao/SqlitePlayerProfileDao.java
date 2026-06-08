package com.deadzone.core.database.dao;

import com.deadzone.core.database.Database;
import com.deadzone.core.profile.PlayerClass;
import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.core.profile.ProfileSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.UUID;

public class SqlitePlayerProfileDao implements PlayerProfileDao {

    private static final String UPSERT_PLAYER = """
        INSERT INTO players
            (uuid, name, infected, infection_level, sanity, player_class, xp, total_xp_earned, first_join, last_seen)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(uuid) DO UPDATE SET
            name            = excluded.name,
            infected        = excluded.infected,
            infection_level = excluded.infection_level,
            sanity          = excluded.sanity,
            player_class    = excluded.player_class,
            xp              = excluded.xp,
            total_xp_earned = excluded.total_xp_earned,
            last_seen       = excluded.last_seen
        """;

    private final Database database;

    public SqlitePlayerProfileDao(Database database) {
        this.database = database;
    }

    @Override
    public PlayerProfile load(UUID uuid) {
        try (Connection c = database.getConnection()) {
            PlayerProfile profile;
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    profile = new PlayerProfile(uuid);
                    profile.setLastKnownName(rs.getString("name"));
                    profile.setInfected(rs.getInt("infected") != 0);
                    profile.setInfectionLevel(rs.getDouble("infection_level"));
                    profile.setSanity(rs.getDouble("sanity"));
                    profile.setPlayerClass(PlayerClass.fromString(rs.getString("player_class")));
                    profile.setXp(rs.getLong("xp"));
                    profile.setTotalXpEarned(rs.getLong("total_xp_earned"));
                    profile.setFirstJoin(rs.getLong("first_join"));
                    profile.setLastSeen(rs.getLong("last_seen"));
                }
            }

            try (PreparedStatement ps = c.prepareStatement("SELECT skill_id FROM player_skills WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        profile.unlockSkill(rs.getString("skill_id"));
                    }
                }
            }

            profile.setDirty(false);
            return profile;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao carregar perfil " + uuid, e);
        }
    }

    @Override
    public void save(ProfileSnapshot snap) {
        try (Connection c = database.getConnection()) {
            c.setAutoCommit(false);
            try {
                writePlayer(c, snap);
                writeSkills(c, snap);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar perfil " + snap.uuid(), e);
        }
    }

    @Override
    public void saveAll(Collection<ProfileSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return;
        }
        try (Connection c = database.getConnection()) {
            c.setAutoCommit(false);
            try {
                for (ProfileSnapshot snap : snapshots) {
                    writePlayer(c, snap);
                    writeSkills(c, snap);
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar perfis em lote", e);
        }
    }

    @Override
    public void delete(UUID uuid) {
        try (Connection c = database.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps1 = c.prepareStatement("DELETE FROM player_skills WHERE uuid = ?");
                 PreparedStatement ps2 = c.prepareStatement("DELETE FROM players WHERE uuid = ?")) {
                ps1.setString(1, uuid.toString());
                ps1.executeUpdate();
                ps2.setString(1, uuid.toString());
                ps2.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao apagar perfil " + uuid, e);
        }
    }

    private void writePlayer(Connection c, ProfileSnapshot snap) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(UPSERT_PLAYER)) {
            ps.setString(1, snap.uuid().toString());
            ps.setString(2, snap.name());
            ps.setInt(3, snap.infected() ? 1 : 0);
            ps.setDouble(4, snap.infectionLevel());
            ps.setDouble(5, snap.sanity());
            ps.setString(6, snap.playerClass().name());
            ps.setLong(7, snap.xp());
            ps.setLong(8, snap.totalXpEarned());
            ps.setLong(9, snap.firstJoin());
            ps.setLong(10, snap.lastSeen());
            ps.executeUpdate();
        }
    }

    private void writeSkills(Connection c, ProfileSnapshot snap) throws SQLException {
        try (PreparedStatement del = c.prepareStatement("DELETE FROM player_skills WHERE uuid = ?")) {
            del.setString(1, snap.uuid().toString());
            del.executeUpdate();
        }
        if (snap.skills().isEmpty()) {
            return;
        }
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO player_skills (uuid, skill_id, unlocked_at) VALUES (?, ?, ?)")) {
            long now = System.currentTimeMillis();
            for (String skillId : snap.skills()) {
                ins.setString(1, snap.uuid().toString());
                ins.setString(2, skillId);
                ins.setLong(3, now);
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }
}
