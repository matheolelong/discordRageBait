package com.ragebait;

import java.sql.*;

public class ConfigManager {

    public static void saveConfig(GhostPingManager gp) {
        QuiExclusionManager qe = QuiExclusionManager.getInstance();
        saveAll(gp, qe);
    }

    public static void saveQuiExclusions(QuiExclusionManager qe) {
        GhostPingManager gp = GhostPingManager.getInstance();
        saveAll(gp, qe);
    }

    private static void saveAll(GhostPingManager gp, QuiExclusionManager qe) {
        try (Connection conn = db().getConnection()) {
            conn.setAutoCommit(false);

            // --- config_settings : target et interval ---
            String upsertSetting = """
                INSERT INTO config_settings (key, value)
                VALUES (?, ?)
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value
                """;

            try (PreparedStatement ps = conn.prepareStatement(upsertSetting)) {
                if (gp.getTargetUserId() != null) {
                    ps.setString(1, "target");
                    ps.setString(2, String.valueOf(gp.getTargetUserId()));
                    ps.executeUpdate();
                }
                ps.setString(1, "interval");
                ps.setString(2, String.valueOf(gp.getInterval()));
                ps.executeUpdate();
            }

            // --- config_channels : remise à zéro puis réinsertion ---
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM config_channels");
            }
            if (!gp.getChannelIds().isEmpty()) {
                String insertChannel = "INSERT INTO config_channels (channel_id) VALUES (?) ON CONFLICT DO NOTHING";
                try (PreparedStatement ps = conn.prepareStatement(insertChannel)) {
                    for (Long channelId : gp.getChannelIds()) {
                        ps.setLong(1, channelId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            // --- qui_exclusions ---
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM qui_exclusions");
            }
            if (!qe.getExcludedUsers().isEmpty()) {
                String insertExcl = "INSERT INTO qui_exclusions (user_id) VALUES (?) ON CONFLICT DO NOTHING";
                try (PreparedStatement ps = conn.prepareStatement(insertExcl)) {
                    for (Long userId : qe.getExcludedUsers()) {
                        ps.setLong(1, userId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            conn.commit();
            System.out.println("[Config] Sauvegardé en base de données.");

        } catch (SQLException e) {
            System.err.println("[Config] Erreur sauvegarde: " + e.getMessage());
        }
    }

    public static void loadConfig(GhostPingManager gp) {
        QuiExclusionManager qe = QuiExclusionManager.getInstance();

        try (Connection conn = db().getConnection()) {

            // --- config_settings ---
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT key, value FROM config_settings")) {
                while (rs.next()) {
                    String key = rs.getString("key");
                    String value = rs.getString("value");
                    switch (key) {
                        case "target" -> gp.setTargetUser(Long.parseLong(value));
                        case "interval" -> gp.setInterval(Integer.parseInt(value));
                    }
                }
            }

            // --- config_channels ---
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT channel_id FROM config_channels")) {
                while (rs.next()) {
                    gp.addChannel(rs.getLong("channel_id"));
                }
            }

            // --- qui_exclusions ---
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT user_id FROM qui_exclusions")) {
                while (rs.next()) {
                    qe.addExclusion(rs.getLong("user_id"));
                }
            }

            System.out.println("[Config] Chargé! " + gp.getChannelIds().size()
                + " salon(s), " + qe.getExcludedUsers().size() + " exclusion(s).");

        } catch (SQLException e) {
            System.err.println("[Config] Erreur chargement: " + e.getMessage());
        }
    }

    private static DatabaseManager db() {
        return DatabaseManager.getInstance();
    }
}
