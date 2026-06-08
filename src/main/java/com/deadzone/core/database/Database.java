package com.deadzone.core.database;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

/** Pool de conexões (HikariCP) sobre um banco SQLite em arquivo. */
public class Database {

    private final DeadzonePlugin plugin;
    private final ConfigManager config;
    private HikariDataSource dataSource;

    public Database(DeadzonePlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void connect() {
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
