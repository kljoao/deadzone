package com.deadzone.core.database;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

/** Pool de conexões (HikariCP). Suporta SQLite (arquivo) ou PostgreSQL (rede), via config. */
public class Database {

    private final DeadzonePlugin plugin;
    private final ConfigManager config;
    private HikariDataSource dataSource;
    private boolean postgres;

    public Database(DeadzonePlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** true quando o banco ativo é PostgreSQL (usado pelo schema p/ dialeto, quando necessário). */
    public boolean isPostgres() {
        return postgres;
    }

    public void connect() {
        if ("postgresql".equals(config.dbType()) || "postgres".equals(config.dbType())) {
            connectPostgres();
        } else {
            connectSqlite();
        }
    }

    private void connectPostgres() {
        this.postgres = true;
        HikariConfig hc = new HikariConfig();
        hc.setPoolName("Deadzone-Postgres");
        hc.setDriverClassName("org.postgresql.Driver");
        hc.setJdbcUrl("jdbc:postgresql://" + config.pgHost() + ":" + config.pgPort() + "/" + config.pgDatabase());
        hc.setUsername(config.pgUsername());
        hc.setPassword(config.pgPassword());
        hc.setMaximumPoolSize(config.dbPoolSize());
        if (config.pgSsl()) {
            hc.addDataSourceProperty("ssl", "true");
            // Provedores na nuvem usam certificados válidos; aceita sem exigir o cert local.
            hc.addDataSourceProperty("sslmode", "require");
        }
        this.dataSource = new HikariDataSource(hc);
        plugin.getLogger().info("Banco: PostgreSQL em " + config.pgHost() + ":" + config.pgPort()
                + "/" + config.pgDatabase() + " (pool " + config.dbPoolSize() + ").");
    }

    private void connectSqlite() {
        this.postgres = false;
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Não foi possível criar a pasta do plugin.");
        }
        File dbFile = new File(plugin.getDataFolder(), "database.db");

        HikariConfig hc = new HikariConfig();
        hc.setPoolName("Deadzone-SQLite");
        hc.setDriverClassName("org.sqlite.JDBC");
        hc.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        hc.setMaximumPoolSize(config.dbPoolSize());

        // PRAGMAs aplicados a cada conexão (xerial lê estas propriedades).
        hc.addDataSourceProperty("journal_mode", "WAL");
        hc.addDataSourceProperty("synchronous", "NORMAL");
        hc.addDataSourceProperty("busy_timeout", "5000");
        hc.addDataSourceProperty("foreign_keys", "true");

        this.dataSource = new HikariDataSource(hc);
        plugin.getLogger().info("Banco: SQLite (arquivo) em " + dbFile.getName() + ".");
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Pool de conexões não inicializado.");
        }
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
