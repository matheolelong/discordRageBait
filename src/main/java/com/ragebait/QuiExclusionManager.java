package com.ragebait;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;

public class QuiExclusionManager {

    private static QuiExclusionManager instance;
    // Cache en mémoire (chargé depuis la DB au démarrage via ConfigManager.loadConfig)
    private final Set<Long> excludedUsers = new HashSet<>();

    private QuiExclusionManager() {}

    public static QuiExclusionManager getInstance() {
        if (instance == null) {
            instance = new QuiExclusionManager();
        }
        return instance;
    }

    public void addExclusion(long userId) {
        excludedUsers.add(userId);
        String sql = "INSERT INTO qui_exclusions (user_id) VALUES (?) ON CONFLICT DO NOTHING";
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[QuiExclusion] Erreur addExclusion: " + e.getMessage());
        }
    }

    public void removeExclusion(long userId) {
        excludedUsers.remove(userId);
        String sql = "DELETE FROM qui_exclusions WHERE user_id = ?";
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[QuiExclusion] Erreur removeExclusion: " + e.getMessage());
        }
    }

    public boolean isExcluded(long userId) {
        return excludedUsers.contains(userId);
    }

    public Set<Long> getExcludedUsers() {
        return new HashSet<>(excludedUsers);
    }

    public void clearExclusions() {
        excludedUsers.clear();
        try (Connection conn = db().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM qui_exclusions");
        } catch (SQLException e) {
            System.err.println("[QuiExclusion] Erreur clearExclusions: " + e.getMessage());
        }
    }

    private DatabaseManager db() {
        return DatabaseManager.getInstance();
    }
}
