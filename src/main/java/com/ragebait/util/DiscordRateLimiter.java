package com.ragebait.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter global pour les actions Discord.
 *
 * <p>Discord impose des limites globales :</p>
 * <ul>
 *   <li>~50 requêtes/seconde globalement</li>
 *   <li>~5 requêtes/seconde par route spécifique (ex: edit message)</li>
 *   <li>Cloudflare ban au-delà de ~10_000 requêtes/10min</li>
 * </ul>
 *
 * <p>Cette classe implémente une file d'attente avec un exécuteur qui :
 * <ul>
 *   <li>Limite à {@value MAX_ACTIONS_PER_SECOND} actions Discord par seconde</li>
 *   <li>Ajoute un délai minimum de {@value MIN_DELAY_MS}ms entre chaque action</li>
 *   <li>Exécute toutes les actions sur un thread dédié → pas de concurrence</li>
 * </ul>
 *
 * <p>Usage recommandé : envelopper les blocs {@code .queue()} dans {@code submit()} :</p>
 * <pre>{@code
 * DiscordRateLimiter.getInstance().submit(() ->
 *     message.editMessage("...").queue()
 * );
 * }</pre>
 */
public class DiscordRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(DiscordRateLimiter.class);

    /** Nombre maximum d'actions Discord par seconde (soft limit) */
    private static final int MAX_ACTIONS_PER_SECOND = 8;

    /** Délai minimum en millisecondes entre deux actions Discord */
    private static final long MIN_DELAY_MS = 125; // 1000ms / 8 actions = 125ms min

    // =========================================================================
    // Singleton
    // =========================================================================

    private static DiscordRateLimiter instance;

    private DiscordRateLimiter() {}

    public static synchronized DiscordRateLimiter getInstance() {
        if (instance == null) {
            instance = new DiscordRateLimiter();
        }
        return instance;
    }

    // =========================================================================
    // File d'attente + thread dédié
    // =========================================================================

    /** File bornée : au-delà de 100 actions en attente, on rejette les nouvelles */
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(100);

    /** Compteur pour les statistiques */
    private final AtomicInteger totalSubmitted = new AtomicInteger(0);
    private final AtomicInteger totalRejected  = new AtomicInteger(0);

    /** Thread unique qui consomme la file à rythme limité */
    private final Thread worker = new Thread(this::runWorker, "discord-rate-limiter");

    {
        worker.setDaemon(true);
        worker.start();
    }

    // =========================================================================
    // API publique
    // =========================================================================

    /**
     * Soumet une action Discord à la file d'attente.
     * L'action sera exécutée dans l'ordre d'arrivée, avec un délai minimum entre chaque envoi.
     *
     * <p>Si la file est pleine (100+ actions en attente), l'action est rejetée et loggée.</p>
     *
     * @param action l'action Discord à effectuer (ex: {@code () -> message.editMessage(...).queue()})
     */
    public void submit(Runnable action) {
        totalSubmitted.incrementAndGet();
        boolean accepted = queue.offer(action);
        if (!accepted) {
            totalRejected.incrementAndGet();
            log.warn("[RateLimiter] File pleine ! Action rejetée. Total rejeté: {}", totalRejected.get());
        }
    }

    /**
     * Boucle principale du worker thread.
     * Consomme la file et attend MIN_DELAY_MS entre chaque action.
     */
    private void runWorker() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Attendre la prochaine action (bloquant)
                Runnable action = queue.take();

                long start = System.currentTimeMillis();
                try {
                    action.run();
                } catch (Exception e) {
                    log.error("[RateLimiter] Erreur lors de l'exécution de l'action Discord", e);
                }

                // Respecter le délai minimum entre deux actions
                long elapsed = System.currentTimeMillis() - start;
                long sleep = MIN_DELAY_MS - elapsed;
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("[RateLimiter] Worker thread interrompu.");
                break;
            }
        }
    }

    /**
     * Retourne le nombre d'actions actuellement en attente dans la file.
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * Retourne les statistiques du rate limiter.
     */
    public String getStats() {
        return String.format("[RateLimiter] Soumises: %d | Rejetées: %d | En attente: %d",
                totalSubmitted.get(), totalRejected.get(), queue.size());
    }
}
