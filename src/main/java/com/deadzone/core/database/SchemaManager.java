package com.deadzone.core.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Cria/migra o schema do banco; a versão fica em schema_version. */
public class SchemaManager {

    private static final int CURRENT_VERSION = 1;

    private final Database database;

    public SchemaManager(Database database) {
        this.database = database;
    }

    public void migrate() throws SQLException {
        try (Connection c = database.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid              TEXT PRIMARY KEY,
                    name              TEXT,
                    infected          INTEGER NOT NULL DEFAULT 0,
                    infection_level   REAL    NOT NULL DEFAULT 0,
                    sanity            REAL    NOT NULL DEFAULT 100,
                    player_class      TEXT    NOT NULL DEFAULT 'NONE',
                    xp                INTEGER NOT NULL DEFAULT 0,
                    total_xp_earned   INTEGER NOT NULL DEFAULT 0,
                    first_join        INTEGER,
                    last_seen         INTEGER,
                    downed_until      INTEGER NOT NULL DEFAULT 0
                )
                """);

            // Migração: garante a coluna downed_until em bancos antigos.
            try {
                st.execute("ALTER TABLE players ADD COLUMN downed_until INTEGER NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
                // coluna já existe
            }

            st.execute("""
                CREATE TABLE IF NOT EXISTS player_skills (
                    uuid        TEXT NOT NULL,
                    skill_id    TEXT NOT NULL,
                    unlocked_at INTEGER,
                    PRIMARY KEY (uuid, skill_id),
                    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER NOT NULL
                )
                """);

            // grava a versão atual se a tabela estiver vazia
            var rs = st.executeQuery("SELECT COUNT(*) FROM schema_version");
            rs.next();
            if (rs.getInt(1) == 0) {
                st.execute("INSERT INTO schema_version (version) VALUES (" + CURRENT_VERSION + ")");
            }
            rs.close();
        }
    }
}
