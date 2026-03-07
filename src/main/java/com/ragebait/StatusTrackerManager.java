package com.ragebait;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class StatusTrackerManager {

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
    }

    public void setNotificationChannel(long channelId) {
        this.notificationChannelId = channelId;
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
            System.out.println("Status Tracker: Cible ou salon non défini!");
            return;
        }
        enabled = true;
        
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
    }

    public void disable() {
        enabled = false;
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
        
        String message = "📝 **Nouvelle citation de " + userName + " :**\n> " + newCustomStatus;
        channel.sendMessage(message).queue();
    }
}
