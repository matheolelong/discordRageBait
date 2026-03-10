package com.ragebait;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private static DatabaseManager instance;

    private final String url;
    private final String user;
    private final String password;

    private DatabaseManager() {
        String host = System.getenv("DB_HOST");
        String port = System.getenv("DB_PORT");
        String name = System.getenv("DB_NAME");
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        if (host == null || port == null || name == null || user == null || password == null) {
            throw new IllegalStateException(
                    "Variables d'environnement manquantes ! Définissez DB_HOST, DB_PORT, DB_NAME, DB_USER et DB_PASSWORD.");
        }

        this.url = "jdbc:postgresql://" + host + ":" + port + "/" + name;
        this.user = user;
        this.password = password;

        initTables();
        log.info("[DB] Connexion PostgreSQL établie à {}", url);
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    // Crée les tables si elles n'existent pas encore
    private void initTables() {
        String sql = """
                CREATE TABLE IF NOT EXISTS casino_balances (
                    user_id  BIGINT PRIMARY KEY,
                    balance  BIGINT NOT NULL DEFAULT 1000
                );

                CREATE TABLE IF NOT EXISTS casino_daily (
                    user_id    BIGINT PRIMARY KEY,
                    last_claim BIGINT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS config_settings (
                    key   VARCHAR(64) PRIMARY KEY,
                    value VARCHAR(256) NOT NULL
                );

                CREATE TABLE IF NOT EXISTS config_channels (
                    channel_id BIGINT PRIMARY KEY
                );

                CREATE TABLE IF NOT EXISTS qui_exclusions (
                    user_id BIGINT PRIMARY KEY
                );
                """;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("[DB] Tables initialisées.");
        } catch (SQLException e) {
            throw new RuntimeException("Erreur initialisation des tables PostgreSQL: " + e.getMessage(), e);
        }
    }
}
