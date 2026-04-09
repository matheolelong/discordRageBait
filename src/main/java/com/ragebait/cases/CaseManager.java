package com.ragebait.cases;

import com.ragebait.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Point d'entree principal pour le systeme de caisses.
 *
 * <p>Responsabilites :</p>
 * <ul>
 *   <li>Charger les caisses depuis {@code cases/cases.json} au demarrage</li>
 *   <li>Gerer l'inventaire des caisses en BDD (table {@code case_inventory})</li>
 *   <li>Gerer l'inventaire des armes droppees en BDD (table {@code weapon_inventory})</li>
 * </ul>
 */
public class CaseManager {

    private static final Logger log = LoggerFactory.getLogger(CaseManager.class);

    private static CaseManager instance;

    /** Map nom -> Case chargee depuis le fichier JSON */
    private final Map<String, Case> loadedCases = new LinkedHashMap<>();

    private CaseManager() {
        initTables();
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
     * Recharge les caisses depuis {@code cases/cases.json}.
     * Peut etre appele en cours d'execution pour prendre en compte des modifications.
     */
    public void reload() {
        loadedCases.clear();
        List<Case> cases = new CaseLoader().loadCases();
        for (Case c : cases) {
            loadedCases.put(c.getName().toLowerCase(), c);
        }
        log.info("[CaseManager] {} caisse(s) chargee(s) : {}", loadedCases.size(), loadedCases.keySet());
    }

    /**
     * Retourne une caisse par son nom (insensible a la casse).
     *
     * @param name nom de la caisse (ex: "alpha_case")
     * @return la caisse, ou null si introuvable
     */
    public Case getCase(String name) {
        return loadedCases.get(name.toLowerCase());
    }

    /** Retourne toutes les caisses disponibles. */
    public Collection<Case> getAllCases() {
        return Collections.unmodifiableCollection(loadedCases.values());
    }

    // =========================================================================
    // Inventaire des CAISSES (case_inventory)
    // =========================================================================

    /**
     * Retourne la quantite d'une caisse dans l'inventaire d'un joueur.
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
     * Ajoute une quantite d'une caisse a l'inventaire d'un joueur.
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
     * Retire une caisse de l'inventaire du joueur.
     *
     * @return true si retire avec succes, false si non possedee
     */
    public boolean removeFromInventory(long userId, String caseName) {
        int current = getInventoryCount(userId, caseName);
        if (current <= 0) return false;

        String sql = current == 1
                ? "DELETE FROM case_inventory WHERE user_id = ? AND case_name = ?"
                : "UPDATE case_inventory SET quantity = quantity - 1 WHERE user_id = ? AND case_name = ?";
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, caseName.toLowerCase());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            log.error("[CaseManager] Erreur removeFromInventory userId={} case={}", userId, caseName, e);
            return false;
        }
    }

    /**
     * Retourne tout l'inventaire de caisses d'un joueur (nom -> quantite).
     */
    public Map<String, Integer> getFullCaseInventory(long userId) {
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
            log.error("[CaseManager] Erreur getFullCaseInventory userId={}", userId, e);
        }
        return inventory;
    }

    // =========================================================================
    // Inventaire des ARMES droppees (weapon_inventory)
    // =========================================================================

    /**
     * Ajoute une arme droppee dans l'inventaire du joueur.
     *
     * @return l'ID genere par PostgreSQL (SERIAL), ou -1 en cas d'erreur
     */
    public int addWeaponDrop(long userId, String weaponName, String caseName,
                              String quality, double floatValue, long price) {
        String sql = """
                INSERT INTO weapon_inventory (user_id, weapon_name, case_name, quality, float_value, price)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """;
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, weaponName);
            ps.setString(3, caseName.toLowerCase());
            ps.setString(4, quality);
            ps.setDouble(5, floatValue);
            ps.setLong(6, price);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        } catch (SQLException e) {
            log.error("[CaseManager] Erreur addWeaponDrop userId={} weapon={}", userId, weaponName, e);
        }
        return -1;
    }

    /**
     * Retourne toutes les armes dans l'inventaire d'un joueur, triees par prix decroissant.
     */
    public List<WeaponDrop> getWeaponInventory(long userId) {
        List<WeaponDrop> drops = new ArrayList<>();
        String sql = """
                SELECT id, user_id, weapon_name, case_name, quality, float_value, price
                FROM weapon_inventory
                WHERE user_id = ?
                ORDER BY price DESC, id ASC
                """;
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    drops.add(mapWeaponDrop(rs));
                }
            }
        } catch (SQLException e) {
            log.error("[CaseManager] Erreur getWeaponInventory userId={}", userId, e);
        }
        return drops;
    }

    /**
     * Retourne une arme specifique par son ID, uniquement si elle appartient au joueur.
     * Retourne null si l'arme n'existe pas ou n'appartient pas au joueur.
     *
     * @param weaponId ID de l'arme (genere par SERIAL)
     * @param userId   ID Discord du joueur (securite)
     */
    public WeaponDrop getWeaponById(int weaponId, long userId) {
        String sql = """
                SELECT id, user_id, weapon_name, case_name, quality, float_value, price
                FROM weapon_inventory
                WHERE id = ? AND user_id = ?
                """;
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, weaponId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapWeaponDrop(rs);
            }
        } catch (SQLException e) {
            log.error("[CaseManager] Erreur getWeaponById id={} userId={}", weaponId, userId, e);
        }
        return null;
    }

    /**
     * Supprime une arme de l'inventaire (apres vente).
     * Verifie que l'arme appartient bien au joueur avant suppression.
     *
     * @return true si supprimee, false si introuvable ou non autorisee
     */
    public boolean removeWeapon(int weaponId, long userId) {
        String sql = "DELETE FROM weapon_inventory WHERE id = ? AND user_id = ?";
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, weaponId);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("[CaseManager] Erreur removeWeapon id={} userId={}", weaponId, userId, e);
            return false;
        }
    }

    /** Mappe une ligne ResultSet en WeaponDrop. */
    private WeaponDrop mapWeaponDrop(ResultSet rs) throws SQLException {
        return new WeaponDrop(
                rs.getInt("id"),
                rs.getLong("user_id"),
                rs.getString("weapon_name"),
                rs.getString("case_name"),
                rs.getString("quality"),
                rs.getDouble("float_value"),
                rs.getLong("price")
        );
    }

    // =========================================================================
    // Initialisation des tables
    // =========================================================================

    private void initTables() {
        String sql = """
                CREATE TABLE IF NOT EXISTS case_inventory (
                    user_id   BIGINT      NOT NULL,
                    case_name VARCHAR(64) NOT NULL,
                    quantity  INT         NOT NULL DEFAULT 1 CHECK (quantity >= 0),
                    PRIMARY KEY (user_id, case_name)
                );

                CREATE INDEX IF NOT EXISTS idx_case_inventory_user ON case_inventory (user_id);

                CREATE TABLE IF NOT EXISTS weapon_inventory (
                    id          SERIAL PRIMARY KEY,
                    user_id     BIGINT          NOT NULL,
                    weapon_name VARCHAR(128)     NOT NULL,
                    case_name   VARCHAR(64)      NOT NULL,
                    quality     VARCHAR(32)      NOT NULL,
                    float_value DOUBLE PRECISION NOT NULL,
                    price       BIGINT          NOT NULL
                );

                CREATE INDEX IF NOT EXISTS idx_weapon_inventory_user ON weapon_inventory (user_id);
                """;
        try (Connection conn = db().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("[CaseManager] Tables 'case_inventory' et 'weapon_inventory' initialisees.");
        } catch (SQLException e) {
            log.error("[CaseManager] Erreur initialisation tables.", e);
        }
    }

    private DatabaseManager db() {
        return DatabaseManager.getInstance();
    }
}
