package com.ragebait;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class RandomMuteManager {

    private static RandomMuteManager instance;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Random random = new Random();
    
    private ScheduledFuture<?> muteTask;
    
    private JDA jda;
    private Long targetUserId;
    private Long guildId;
    private int maxDelaySeconds = 30;
    private int muteDurationMs = 500; // Durée du mute en ms
    private boolean running = false;

    private RandomMuteManager() {}

    public static RandomMuteManager getInstance() {
        if (instance == null) {
            instance = new RandomMuteManager();
        }
        return instance;
    }

    public void setJda(JDA jda) {
        this.jda = jda;
    }

    public void setTarget(long userId, long guildId) {
        this.targetUserId = userId;
        this.guildId = guildId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public void setMaxDelay(int seconds) {
        this.maxDelaySeconds = seconds;
    }

    public int getMaxDelay() {
        return maxDelaySeconds;
    }

    public void setMuteDuration(int ms) {
        this.muteDurationMs = ms;
    }

    public int getMuteDuration() {
        return muteDurationMs;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running) {
            return;
        }
        
        if (targetUserId == null || guildId == null) {
            System.out.println("Random Mute: Aucune cible définie!");
            return;
        }

        running = true;
        scheduleNextMute();
        System.out.println("Random Mute démarré! Cible: " + targetUserId + ", Délai max: " + maxDelaySeconds + "s");
    }

    public void stop() {
        if (muteTask != null) {
            muteTask.cancel(false);
            muteTask = null;
        }
        running = false;
        System.out.println("Random Mute arrêté!");
    }

    private void scheduleNextMute() {
        if (!running || jda == null) {
            return;
        }

        // Délai aléatoire entre 1 et maxDelaySeconds
        int delay = 1 + random.nextInt(Math.max(1, maxDelaySeconds));
        
        muteTask = scheduler.schedule(() -> {
            performRandomMute();
        }, delay, TimeUnit.SECONDS);
        
        System.out.println("Prochain mute dans " + delay + " secondes");
    }

    private void performRandomMute() {
        if (!running || jda == null || guildId == null || targetUserId == null) {
            return;
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            scheduleNextMute();
            return;
        }

        Member member = guild.getMemberById(targetUserId);
        if (member == null || member.getVoiceState() == null || !member.getVoiceState().inAudioChannel()) {
            // L'utilisateur n'est pas en vocal, on replanifie
            scheduleNextMute();
            return;
        }

        // Choix aléatoire : mute vocal (0) ou mute casque/deafen (1)
        boolean deafen = random.nextBoolean();
        
        try {
            if (deafen) {
                // Mute casque (deafen)
                guild.deafen(member, true).queue(
                    success -> {
                        System.out.println("Deafen appliqué à " + member.getEffectiveName());
                        // Unmute après la durée
                        scheduler.schedule(() -> {
                            guild.deafen(member, false).queue();
                        }, muteDurationMs, TimeUnit.MILLISECONDS);
                    },
                    error -> System.out.println("Erreur deafen: " + error.getMessage())
                );
            } else {
                // Mute vocal
                guild.mute(member, true).queue(
                    success -> {
                        System.out.println("Mute appliqué à " + member.getEffectiveName());
                        // Unmute après la durée
                        scheduler.schedule(() -> {
                            guild.mute(member, false).queue();
                        }, muteDurationMs, TimeUnit.MILLISECONDS);
                    },
                    error -> System.out.println("Erreur mute: " + error.getMessage())
                );
            }
        } catch (Exception e) {
            System.out.println("Erreur lors du mute: " + e.getMessage());
        }

        // Planifier le prochain mute
        scheduleNextMute();
    }

    public void shutdown() {
        stop();
        scheduler.shutdown();
    }
}
