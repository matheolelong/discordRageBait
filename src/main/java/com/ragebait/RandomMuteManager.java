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
    
    private ScheduledExecutorService scheduler;
    private final Random random = new Random();
    
    private ScheduledFuture<?> muteTask;
    
    private JDA jda;
    private Long targetUserId;
    private Long guildId;
    private int maxDelaySeconds = 30;
    private int muteDurationMs = 500;
    private boolean running = false;

    private RandomMuteManager() {
        initScheduler();
    }
    
    private void initScheduler() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(2);
        }
    }

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
        // Arrêter si déjà en cours
        if (running) {
            stop();
        }
        
        if (targetUserId == null || guildId == null) {
            System.out.println("Random Mute: Aucune cible définie!");
            return;
        }

        initScheduler();
        running = true;
        scheduleNextMute();
        System.out.println("Random Mute démarré! Cible: " + targetUserId + ", Délai max: " + maxDelaySeconds + "s");
    }

    public void stop() {
        running = false;
        if (muteTask != null) {
            muteTask.cancel(true);
            muteTask = null;
        }
        System.out.println("Random Mute arrêté!");
    }

    private void scheduleNextMute() {
        if (!running || jda == null) {
            return;
        }

        initScheduler();
        
        int delay = 1 + random.nextInt(Math.max(1, maxDelaySeconds));
        
        try {
            muteTask = scheduler.schedule(() -> {
                try {
                    performRandomMute();
                } catch (Exception e) {
                    System.err.println("Erreur dans performRandomMute: " + e.getMessage());
                    if (running) {
                        scheduleNextMute();
                    }
                }
            }, delay, TimeUnit.SECONDS);
            
            System.out.println("Prochain mute dans " + delay + " secondes");
        } catch (Exception e) {
            System.err.println("Erreur scheduling: " + e.getMessage());
            initScheduler();
            if (running) {
                scheduleNextMute();
            }
        }
    }

    private void performRandomMute() {
        if (!running || jda == null || guildId == null || targetUserId == null) {
            return;
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            if (running) scheduleNextMute();
            return;
        }

        Member member = guild.getMemberById(targetUserId);
        if (member == null || member.getVoiceState() == null || !member.getVoiceState().inAudioChannel()) {
            if (running) scheduleNextMute();
            return;
        }

        boolean deafen = random.nextBoolean();
        
        try {
            if (deafen) {
                guild.deafen(member, true).queue(
                    success -> {
                        System.out.println("Deafen appliqué à " + member.getEffectiveName());
                        scheduler.schedule(() -> {
                            try {
                                guild.deafen(member, false).queue();
                            } catch (Exception ignored) {}
                        }, muteDurationMs, TimeUnit.MILLISECONDS);
                    },
                    error -> System.out.println("Erreur deafen: " + error.getMessage())
                );
            } else {
                guild.mute(member, true).queue(
                    success -> {
                        System.out.println("Mute appliqué à " + member.getEffectiveName());
                        scheduler.schedule(() -> {
                            try {
                                guild.mute(member, false).queue();
                            } catch (Exception ignored) {}
                        }, muteDurationMs, TimeUnit.MILLISECONDS);
                    },
                    error -> System.out.println("Erreur mute: " + error.getMessage())
                );
            }
        } catch (Exception e) {
            System.out.println("Erreur lors du mute: " + e.getMessage());
        }

        if (running) {
            scheduleNextMute();
        }
    }

    public void shutdown() {
        stop();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}
