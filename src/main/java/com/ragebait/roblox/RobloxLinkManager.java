package com.ragebait.roblox;

import com.ragebait.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Liaison compte Discord <-> compte Roblox.
 * <p>Le jeu demande un code a casinoApi (table roblox_link_codes), le joueur le tape
 * avec /link, et le bot enregistre la liaison dans roblox_links.
 * Les tables sont creees par casinoApi au demarrage.
 */
public class RobloxLinkManager {

    private static final Logger log = LoggerFactory.getLogger(RobloxLinkManager.class);
    private static RobloxLinkManager instance;

    public enum Status { LINKED, INVALID_CODE, ERROR }

    public record LinkResult(Status status, long robloxId) {}

    private RobloxLinkManager() {}

    public static synchronized RobloxLinkManager getInstance() {
        if (instance == null) {
            instance = new RobloxLinkManager();
        }
        return instance;
    }

    /**
     * Lie le compte Discord au compte Roblox associe au code.
     * Remplace une eventuelle ancienne liaison de ce compte Discord.
     */
    public LinkResult link(long discordId, String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        if (code.isEmpty()) {
            return new LinkResult(Status.INVALID_CODE, 0);
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                long robloxId = consumeCode(conn, code);
                if (robloxId < 0) {
                    conn.rollback();
                    return new LinkResult(Status.INVALID_CODE, 0);
                }

                update(conn, "DELETE FROM roblox_links WHERE discord_id = ? OR roblox_id = ?", discordId, robloxId);
                update(conn, "INSERT INTO roblox_links (roblox_id, discord_id) VALUES (?, ?)", robloxId, discordId);

                conn.commit();
                log.info("[Roblox] Discord {} lie au compte Roblox {}", discordId, robloxId);
                return new LinkResult(Status.LINKED, robloxId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("[Roblox] Erreur link pour discordId={}", discordId, e);
            return new LinkResult(Status.ERROR, 0);
        }
    }

    /** Supprime le code s'il est valide et retourne l'ID Roblox associe, sinon -1. */
    private long consumeCode(Connection conn, String code) throws SQLException {
        String sql = "DELETE FROM roblox_link_codes WHERE code = ? AND expires_at > NOW() RETURNING roblox_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("roblox_id") : -1;
            }
        }
    }

    private void update(Connection conn, String sql, long first, long second) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, first);
            ps.setLong(2, second);
            ps.executeUpdate();
        }
    }
}
