package com.ragebait;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class StatusTrackerManager {

    private static final Logger log = LoggerFactory.getLogger(StatusTrackerManager.class);
    private static StatusTrackerManager instance;

    private JDA jda;
    private Long targetUserId;
    private Long guildId;
    private Long notificationChannelId;
    private boolean enabled = false;

    // Stocke le dernier statut personnalisé pour détecter les changements
    private String lastCustomStatus = null;

    private StatusTrackerManager() {}

    public static StatusTrackerManager getInstance() {
        if (instance == null) {
            instance = new StatusTrackerManager();
        }
        return instance;
    }

    public void setJda(JDA jda) {
        this.jda = jda;
    }

    public void setTarget(long userId, long guildId) {
        this.targetUserId = userId;
        this.guildId = guildId;
        this.lastCustomStatus = null;
        saveToDb();
    }

    public void setNotificationChannel(long channelId) {
        this.notificationChannelId = channelId;
        saveToDb();
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public Long getNotificationChannelId() {
        return notificationChannelId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void enable() {
        if (targetUserId == null || notificationChannelId == null) {
            log.warn("[StatusTracker] Cible ou salon de notification non défini.");
            return;
        }
        enabled = true;
        saveToDb();

        // Initialiser le statut actuel
        if (jda != null && guildId != null) {
            var guild = jda.getGuildById(guildId);
            if (guild != null) {
                Member member = guild.getMemberById(targetUserId);
                if (member != null) {
                    for (Activity activity : member.getActivities()) {
                        if (activity.getType() == Activity.ActivityType.CUSTOM_STATUS) {
                            lastCustomStatus = activity.getState() != null ? activity.getState() : activity.getName();
                            break;
                        }
                    }
                }
            }
        }
        log.info("[StatusTracker] Activé. Surveillance de: {}", targetUserId);
    }

    public void disable() {
        enabled = false;
        saveToDb();
        log.info("[StatusTracker] Désactivé.");
    }

    // Sauvegarde l'état du tracker dans la base
    private void saveToDb() {
        if (guildId == null) return;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "INSERT INTO status_tracker (guild_id, user_id, channel_id, enabled) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT (guild_id) DO UPDATE SET user_id=EXCLUDED.user_id, channel_id=EXCLUDED.channel_id, enabled=EXCLUDED.enabled";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, guildId);
                ps.setLong(2, targetUserId != null ? targetUserId : 0);
                ps.setLong(3, notificationChannelId != null ? notificationChannelId : 0);
                ps.setBoolean(4, enabled);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            log.error("[StatusTracker] Erreur saveToDb: {}", e.getMessage());
        }
    }

    // Charge l'état du tracker depuis la base
    public void loadFromDb(long guildId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "SELECT user_id, channel_id, enabled FROM status_tracker WHERE guild_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, guildId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        this.guildId = guildId;
                        this.targetUserId = rs.getLong("user_id");
                        this.notificationChannelId = rs.getLong("channel_id");
                        this.enabled = rs.getBoolean("enabled");
                    }
                }
            }
        } catch (SQLException e) {
            log.error("[StatusTracker] Erreur loadFromDb: {}", e.getMessage());
        }
    }

    public void onPresenceUpdate(Member member) {
        if (!enabled || jda == null || targetUserId == null || notificationChannelId == null) {
            return;
        }

        if (member.getIdLong() != targetUserId) {
            return;
        }

        TextChannel channel = jda.getTextChannelById(notificationChannelId);
        if (channel == null) {
            return;
        }

        String userName = member.getEffectiveName();
        String newCustomStatus = null;

        // Chercher le statut personnalisé actuel
        for (Activity activity : member.getActivities()) {
            if (activity.getType() == Activity.ActivityType.CUSTOM_STATUS) {
                newCustomStatus = activity.getState() != null ? activity.getState() : activity.getName();
                break;
            }
        }

        // Si le nouveau statut est null ou vide, on ignore (suppression)
        if (newCustomStatus == null || newCustomStatus.isEmpty()) {
            lastCustomStatus = null;
            return;
        }

        // Si c'est le même statut qu'avant, on ignore
        if (newCustomStatus.equals(lastCustomStatus)) {
            return;
        }

        // Nouveau statut détecté !
        lastCustomStatus = newCustomStatus;
        log.info("[StatusTracker] Nouveau statut de {}: {}", userName, newCustomStatus);

        String message = "📝 **Nouvelle citation de " + userName + " :**\n> " + newCustomStatus;
        channel.sendMessage(message).queue();
    }
}
