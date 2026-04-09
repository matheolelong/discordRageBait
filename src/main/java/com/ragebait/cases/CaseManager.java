package com.ragebait.cases;

import com.ragebait.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Point d'entrée principal pour le système de caisses.
 *
 * <p>Responsabilités :</p>
 * <ul>
 *   <li>Charger les caisses depuis {@code cases/cases.json} au démarrage</li>
 *   <li>Gérer l'inventaire des caisses en base de données PostgreSQL</li>
 *   <li>Fournir un accès aux caisses disponibles par nom</li>
 * </ul>
 *
 * <p>Table SQL utilisée : {@code case_inventory (user_id, case_name, quantity)}</p>
 */
public class CaseManager {

    private static final Logger log = LoggerFactory.getLogger(CaseManager.class);

    private static CaseManager instance;

    /** Map nom → Case chargée depuis le fichier JSON */
    private final Map<String, Case> loadedCases = new LinkedHashMap<>();

    private CaseManager() {
        // 1. Initialiser la table d'inventaire
        initTable();
        // 2. Charger les caisses depuis le fichier de configuration
        reload();
    }

    public static synchronized CaseManager getInstance() {
        if (instance == null) {
            instance = new CaseManager();
        }
        return instance;
    }

    // =========================================================================
    // Chargement des caisses
    // =========================================================================

    /**
     * Recharge les caisses depuis le fichier {@code cases/cases.json}.
     * Peut être appelé en cours d'exécution pour prendre en compte des modifications.
     */
    public void reload() {
        loadedCases.clear();
        CaseLoader loader = new CaseLoader();
        List<Case> cases = loader.loadCases();
        for (Case c : cases) {
            loadedCases.put(c.getName().toLowerCase(), c);
        }
        log.info("[CaseManager] {} caisse(s) chargée(s) : {}", loadedCases.size(), loadedCases.keySet());
    }

    /**
     * Retourne une caisse par son nom (insensible à la casse).
     *
     * @param name nom de la caisse (ex: "alpha_case")
     * @return la caisse, ou null si introuvable
     */
    public Case getCase(String name) {
        return loadedCases.get(name.toLowerCase());
    }

    /**
     * Retourne toutes les caisses disponibles.
     *
     * @return collection non modifiable des caisses chargées
     */
    public Collection<Case> getAllCases() {
        return Collections.unmodifiableCollection(loadedCases.values());
    }

    // =========================================================================
    // Gestion de l'inventaire (base de données)
    // =========================================================================

    /**
     * Retourne la quantité d'une caisse donnée dans l'inventaire d'un joueur.
     *
     * @param userId   identifiant Discord du joueur
     * @param caseName nom de la caisse
     * @return quantité possédée (0 si aucune)
     */
    public int getInventoryCount(long userId, String caseName) {
        String sql = "SELECT quantity FROM case_inventory WHERE user_id = ? AND case_name = ?";
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, caseName.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("quantity");
            }
        } catch (SQLException e) {
            log.error("[CaseManager] Erreur getInventoryCount userId={} case={}", userId, caseName, e);
        }
        return 0;
    }

    /**
     * Ajoute une quantité d'une caisse à l'inventaire d'un joueur.
     *
     * @param userId   identifiant Discord du joueur
     * @param caseName nom de la caisse
     * @param amount   quantité à ajouter (> 0)
     */
    public void addToInventory(long userId, String caseName, int amount) {
        String sql = """
                INSERT INTO case_inventory (user_id, case_name, quantity)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id, case_name) DO UPDATE
                SET quantity = case_inventory.quantity + EXCLUDED.quantity
                """;
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, caseName.toLowerCase());
            ps.setInt(3, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("[CaseManager] Erreur addToInventory userId={} case={}", userId, caseName, e);
        }
    }

    /**
     * Retire une caisse de l'inventaire du joueur (pour ouverture).
     * Ne fait rien si le joueur n'en possède pas.
     *
     * @param userId   identifiant Discord du joueur
     * @param caseName nom de la caisse
     * @return true si la caisse a été retirée, false si le joueur n'en avait pas
     */
    public boolean removeFromInventory(long userId, String caseName) {
        int current = getInventoryCount(userId, caseName);
        if (current <= 0) return false;

        if (current == 1) {
            // Supprimer la ligne complètement
            String sql = "DELETE FROM case_inventory WHERE user_id = ? AND case_name = ?";
            try (Connection conn = db().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                ps.setString(2, caseName.toLowerCase());
                ps.executeUpdate();
            } catch (SQLException e) {
                log.error("[CaseManager] Erreur removeFromInventory (delete) userId={} case={}", userId, caseName, e);
                return false;
            }
        } else {
            // Décrémenter la quantité
            String sql = "UPDATE case_inventory SET quantity = quantity - 1 WHERE user_id = ? AND case_name = ?";
            try (Connection conn = db().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                ps.setString(2, caseName.toLowerCase());
                ps.executeUpdate();
            } catch (SQLException e) {
                log.error("[CaseManager] Erreur removeFromInventory (update) userId={} case={}", userId, caseName, e);
                return false;
            }
        }
        return true;
    }

    /**
     * Retourne tout l'inventaire de caisses d'un joueur.
     *
     * @param userId identifiant Discord du joueur
     * @return map nom_caisse → quantité (jamais null)
     */
    public Map<String, Integer> getFullInventory(long userId) {
        Map<String, Integer> inventory = new LinkedHashMap<>();
        String sql = "SELECT case_name, quantity FROM case_inventory WHERE user_id = ? AND quantity > 0 ORDER BY case_name";
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    inventory.put(rs.getString("case_name"), rs.getInt("quantity"));
                }
            }
        } catch (SQLException e) {
            log.error("[CaseManager] Erreur getFullInventory userId={}", userId, e);
        }
        return inventory;
    }

    // =========================================================================
    // Initialisation de la base de données
    // =========================================================================

    /** Crée la table d'inventaire des caisses si elle n'existe pas. */
    private void initTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS case_inventory (
                    user_id   BIGINT      NOT NULL,
                    case_name VARCHAR(64) NOT NULL,
                    quantity  INT         NOT NULL DEFAULT 1 CHECK (quantity >= 0),
                    PRIMARY KEY (user_id, case_name)
                );
                """;
        try (Connection conn = db().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("[CaseManager] Table 'case_inventory' initialisée.");
        } catch (SQLException e) {
            log.error("[CaseManager] Erreur initialisation table 'case_inventory'.", e);
        }
    }

    private DatabaseManager db() {
        return DatabaseManager.getInstance();
    }
}
