package com.ragebait;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gestionnaire de connexions BDD via HikariCP (connection pool).
 *
 * <p>Avantages vs DriverManager.getConnection() :</p>
 * <ul>
 *   <li>Réutilise les connexions existantes → évite de créer une connexion JDBC (~50-200ms) à chaque requête</li>
 *   <li>Max 5 connexions simultanées → contrôle la charge sur PostgreSQL</li>
 *   <li>Connection timeout et idle timeout configurés → libère les connexions inutilisées</li>
 * </ul>
 */
public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private static DatabaseManager instance;

    /** Pool de connexions HikariCP (thread-safe) */
    private final HikariDataSource dataSource;

    private DatabaseManager() {
        String host     = System.getenv("DB_HOST");
        String port     = System.getenv("DB_PORT");
        String name     = System.getenv("DB_NAME");
        String user     = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        if (host == null || port == null || name == null || user == null || password == null) {
            throw new IllegalStateException(
                    "Variables d'environnement manquantes ! Définissez DB_HOST, DB_PORT, DB_NAME, DB_USER et DB_PASSWORD.");
        }

        String url = "jdbc:postgresql://" + host + ":" + port + "/" + name;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");

        // Taille du pool : 2 connexions min, 5 max
        config.setMinimumIdle(2);
        config.setMaximumPoolSize(5);

        // Timeouts
        config.setConnectionTimeout(10_000);   // 10s pour obtenir une connexion du pool
        config.setIdleTimeout(600_000);         // 10min avant de fermer une connexion inactive
        config.setMaxLifetime(1_800_000);       // 30min durée de vie max d'une connexion

        // Optimisations PostgreSQL
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        config.setPoolName("RageBait-HikariPool");

        this.dataSource = new HikariDataSource(config);

        initTables();
        log.info("[DB] Pool HikariCP initialisé → {}", url);
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Récupère une connexion depuis le pool.
     * À utiliser dans un try-with-resources pour la rendre automatiquement au pool.
     *
     * @throws SQLException si le pool est épuisé ou la BDD inaccessible
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Ferme proprement le pool (à appeler lors de l'arrêt du bot).
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("[DB] Pool HikariCP fermé.");
        }
    }

    // =========================================================================
    // Initialisation des tables de base
    // =========================================================================

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
