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
            // Autocommit garantido: cada DDL é sua própria transação. Assim o ALTER TABLE que
            // falha ("coluna já existe") NÃO aborta as instruções seguintes no PostgreSQL.
            c.setAutoCommit(true);
            // Tipos portáveis: BIGINT (longs/timestamps em ms) e DOUBLE PRECISION (floats).
            // SQLite aceita por afinidade; PostgreSQL precisa deles (INTEGER de 4 bytes
            // estoura nos timestamps em milissegundos).
            st.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid              TEXT PRIMARY KEY,
                    name              TEXT,
                    infected          INTEGER          NOT NULL DEFAULT 0,
                    infection_level   DOUBLE PRECISION NOT NULL DEFAULT 0,
                    sanity            DOUBLE PRECISION NOT NULL DEFAULT 100,
                    player_class      TEXT             NOT NULL DEFAULT 'NONE',
                    xp                BIGINT           NOT NULL DEFAULT 0,
                    total_xp_earned   BIGINT           NOT NULL DEFAULT 0,
                    first_join        BIGINT,
                    last_seen         BIGINT,
                    downed_until      BIGINT           NOT NULL DEFAULT 0,
                    balance           BIGINT           NOT NULL DEFAULT 0,
                    daily_streak      BIGINT           NOT NULL DEFAULT 0,
                    last_daily_claim  BIGINT           NOT NULL DEFAULT 0,
                    stat_zombies_killed   BIGINT       NOT NULL DEFAULT 0,
                    stat_players_killed   BIGINT       NOT NULL DEFAULT 0,
                    stat_deaths           BIGINT       NOT NULL DEFAULT 0,
                    stat_revives          BIGINT       NOT NULL DEFAULT 0,
                    stat_best_survival_ms BIGINT       NOT NULL DEFAULT 0,
                    life_started_at       BIGINT       NOT NULL DEFAULT 0,
                    bounty                BIGINT       NOT NULL DEFAULT 0
                )
                """);

            // Migração: garante a coluna downed_until em bancos antigos (autocommit -> erro
            // de "coluna já existe" não invalida as instruções seguintes, no SQLite e no PG).
            try {
                st.execute("ALTER TABLE players ADD COLUMN downed_until BIGINT NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
                // coluna já existe
            }
            try {
                st.execute("ALTER TABLE players ADD COLUMN balance BIGINT NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
                // coluna já existe
            }
            // Migração: recompensa diária + estatísticas (cada ALTER é isolado por autocommit).
            for (String col : new String[]{
                    "daily_streak", "last_daily_claim",
                    "stat_zombies_killed", "stat_players_killed", "stat_deaths",
                    "stat_revives", "stat_best_survival_ms", "life_started_at", "bounty"}) {
                try {
                    st.execute("ALTER TABLE players ADD COLUMN " + col + " BIGINT NOT NULL DEFAULT 0");
                } catch (SQLException ignored) {
                    // coluna já existe
                }
            }

            st.execute("""
                CREATE TABLE IF NOT EXISTS player_skills (
                    uuid        TEXT   NOT NULL,
                    skill_id    TEXT   NOT NULL,
                    unlocked_at BIGINT,
                    PRIMARY KEY (uuid, skill_id),
                    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS clans (
                    id            TEXT PRIMARY KEY,
                    name          TEXT    NOT NULL,
                    tag           TEXT    NOT NULL,
                    color         TEXT    NOT NULL DEFAULT 'WHITE',
                    leader_uuid   TEXT    NOT NULL,
                    created_at    BIGINT  NOT NULL DEFAULT 0,
                    bank          BIGINT  NOT NULL DEFAULT 0,
                    level         BIGINT  NOT NULL DEFAULT 1,
                    xp            BIGINT  NOT NULL DEFAULT 0,
                    friendly_fire INTEGER NOT NULL DEFAULT 0,
                    glow          INTEGER NOT NULL DEFAULT 1,
                    symbol        INTEGER NOT NULL DEFAULT 1
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS clan_members (
                    uuid      TEXT PRIMARY KEY,
                    clan_id   TEXT   NOT NULL,
                    name      TEXT,
                    role      TEXT   NOT NULL DEFAULT 'MEMBER',
                    joined_at BIGINT NOT NULL DEFAULT 0,
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
                """);
            try {
                st.execute("CREATE INDEX IF NOT EXISTS idx_clan_members_clan ON clan_members(clan_id)");
            } catch (SQLException ignored) {
                // índice já existe
            }

            st.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER NOT NULL
                )
                """);

            // grava a versão atual se a tabela estiver vazia
            boolean empty;
            try (var rs = st.executeQuery("SELECT COUNT(*) FROM schema_version")) {
                rs.next();
                empty = rs.getInt(1) == 0;
            }
            if (empty) {
                st.execute("INSERT INTO schema_version (version) VALUES (" + CURRENT_VERSION + ")");
            }
        }
    }
}
