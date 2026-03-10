package com.ragebait;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GhostPingManager {

    private static final Logger log = LoggerFactory.getLogger(GhostPingManager.class);
    private static GhostPingManager instance;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> ghostPingTask;

    private JDA jda;
    private Long targetUserId;
    private final Set<Long> channelIds = new HashSet<>();
    private int intervalSeconds = 300;
    private boolean running = false;

    private GhostPingManager() {}

    public static GhostPingManager getInstance() {
        if (instance == null) {
            instance = new GhostPingManager();
        }
        return instance;
    }

    public void setJda(JDA jda) {
        this.jda = jda;
    }

    public void setTargetUser(long userId) {
        this.targetUserId = userId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public void addChannel(long channelId) {
        channelIds.add(channelId);
    }

    public void removeChannel(long channelId) {
        channelIds.remove(channelId);
    }

    public void clearChannels() {
        channelIds.clear();
    }

    public Set<Long> getChannelIds() {
        return new HashSet<>(channelIds);
    }

    public void setInterval(int seconds) {
        this.intervalSeconds = seconds;
        // Redémarrer si déjà en cours
        if (running) {
            stop();
            start();
        }
    }

    public int getInterval() {
        return intervalSeconds;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running) {
            return;
        }

        if (channelIds.isEmpty()) {
            log.warn("[GhostPing] Aucun salon défini, impossible de démarrer.");
            return;
        }

        running = true;

        ghostPingTask = scheduler.scheduleAtFixedRate(() -> {
            sendGhostPings();
        }, 0, intervalSeconds, TimeUnit.SECONDS);

        log.info("[GhostPing] Démarré. Cible: {}, Intervalle: {}s", targetUserId, intervalSeconds);
    }

    public void stop() {
        if (ghostPingTask != null) {
            ghostPingTask.cancel(false);
            ghostPingTask = null;
        }
        running = false;
        log.info("[GhostPing] Arrêté.");
    }

    private void sendGhostPings() {
        if (jda == null || channelIds.isEmpty()) {
            return;
        }

        // Si une cible est définie, on la ping, sinon juste "."
        String pingMessage = (targetUserId != null) ? "<@" + targetUserId + ">" : ".";

        // Choisir un salon aléatoire
        Long[] channelArray = channelIds.toArray(new Long[0]);
        Long randomChannelId = channelArray[(int) (Math.random() * channelArray.length)];

        TextChannel channel = jda.getTextChannelById(randomChannelId);
        if (channel != null) {
            channel.sendMessage(pingMessage).queue(message -> {
                // Supprimer le message après 1 seconde
                scheduler.schedule(() -> {
                    message.delete().queue(
                        success -> log.info("[GhostPing] Ghost ping envoyé et supprimé dans #{}", channel.getName()),
                        error -> log.warn("[GhostPing] Erreur suppression du message: {}", error.getMessage())
                    );
                }, 1, TimeUnit.SECONDS);
            }, error -> {
                log.warn("[GhostPing] Erreur envoi dans salon {}: {}", randomChannelId, error.getMessage());
            });
        } else {
            log.warn("[GhostPing] Salon introuvable: {}", randomChannelId);
        }
    }

    public void shutdown() {
        stop();
        scheduler.shutdown();
    }
}
