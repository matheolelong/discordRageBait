package com.ragebait;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class RandomMuteManager {

    private static final Logger log = LoggerFactory.getLogger(RandomMuteManager.class);
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
            log.warn("[RandomMute] Aucune cible définie, impossible de démarrer.");
            return;
        }

        initScheduler();
        running = true;
        scheduleNextMute();
        log.info("[RandomMute] Démarré. Cible: {}, Délai max: {}s, Durée mute: {}ms",
                targetUserId, maxDelaySeconds, muteDurationMs);
    }

    public void stop() {
        running = false;
        if (muteTask != null) {
            muteTask.cancel(true);
            muteTask = null;
        }
        log.info("[RandomMute] Arrêté.");
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
                    log.error("[RandomMute] Erreur dans performRandomMute", e);
                    if (running) {
                        scheduleNextMute();
                    }
                }
            }, delay, TimeUnit.SECONDS);

            log.debug("[RandomMute] Prochain mute dans {} secondes", delay);
        } catch (Exception e) {
            log.error("[RandomMute] Erreur scheduling", e);
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
                        log.info("[RandomMute] Deafen appliqué à {} pendant {}ms",
                                member.getEffectiveName(), muteDurationMs);
                        scheduler.schedule(() -> {
                            try {
                                guild.deafen(member, false).queue();
                            } catch (Exception ignored) {}
                        }, muteDurationMs, TimeUnit.MILLISECONDS);
                    },
                    error -> log.warn("[RandomMute] Erreur deafen sur {}: {}",
                            member.getEffectiveName(), error.getMessage())
                );
            } else {
                guild.mute(member, true).queue(
                    success -> {
                        log.info("[RandomMute] Mute appliqué à {} pendant {}ms",
                                member.getEffectiveName(), muteDurationMs);
                        scheduler.schedule(() -> {
                            try {
                                guild.mute(member, false).queue();
                            } catch (Exception ignored) {}
                        }, muteDurationMs, TimeUnit.MILLISECONDS);
                    },
                    error -> log.warn("[RandomMute] Erreur mute sur {}: {}",
                            member.getEffectiveName(), error.getMessage())
                );
            }
        } catch (Exception e) {
            log.error("[RandomMute] Erreur lors du mute de {}", member.getEffectiveName(), e);
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
