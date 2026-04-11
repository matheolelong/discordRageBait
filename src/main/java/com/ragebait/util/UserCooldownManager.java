package com.ragebait.util;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire de cooldowns par utilisateur.
 *
 * <p>Empêche le spam de commandes coûteuses (ouverture de caisse, casino)
 * en imposant un délai minimum entre chaque exécution.</p>
 *
 * <p>Usage :</p>
 * <pre>{@code
 * if (UserCooldownManager.getInstance().isOnCooldown(userId, CooldownType.OPEN_CASE)) {
 *     long remaining = UserCooldownManager.getInstance().getRemainingMs(userId, CooldownType.OPEN_CASE);
 *     event.reply("⏳ Attends encore **" + (remaining / 1000) + "s**!").setEphemeral(true).queue();
 *     return;
 * }
 * UserCooldownManager.getInstance().setCooldown(userId, CooldownType.OPEN_CASE);
 * }</pre>
 */
public class UserCooldownManager {

    // =========================================================================
    // Types de cooldown et leurs durées
    // =========================================================================

    public enum CooldownType {
        /** Ouverture de caisse — 4 secondes */
        OPEN_CASE(4_000),
        /** Commandes casino (slots, coinflip, etc.) — 1.5 secondes */
        CASINO(1_500),
        /** Achat de caisse — 2 secondes */
        BUY_CASE(2_000),
        /** Vente d'armes — 2 secondes */
        SELL(2_000);

        private final long durationMs;

        CooldownType(long durationMs) {
            this.durationMs = durationMs;
        }

        public long getDurationMs() {
            return durationMs;
        }
    }

    // =========================================================================
    // Singleton
    // =========================================================================

    private static UserCooldownManager instance;

    private UserCooldownManager() {}

    public static synchronized UserCooldownManager getInstance() {
        if (instance == null) {
            instance = new UserCooldownManager();
        }
        return instance;
    }

    // =========================================================================
    // Stockage — Map<userId, Map<type, expireTimestamp>>
    // =========================================================================

    /**
     * Clé composite : (userId * 100) + type.ordinal()
     * Evite une Map<Long, Map<CooldownType, Long>> plus lourde.
     */
    private final ConcurrentHashMap<Long, Long> cooldowns = new ConcurrentHashMap<>();

    // =========================================================================
    // API publique
    // =========================================================================

    /**
     * Vérifie si l'utilisateur est en cooldown pour ce type d'action.
     *
     * @param userId ID Discord de l'utilisateur
     * @param type   type de cooldown
     * @return true si en cooldown (doit être bloqué), false si peut agir
     */
    public boolean isOnCooldown(long userId, CooldownType type) {
        long key = key(userId, type);
        Long expires = cooldowns.get(key);
        if (expires == null) return false;
        if (System.currentTimeMillis() >= expires) {
            cooldowns.remove(key); // nettoyage paresseux
            return false;
        }
        return true;
    }

    /**
     * Démarre le cooldown pour cet utilisateur et ce type d'action.
     * À appeler <b>immédiatement</b> avant de lancer l'action.
     *
     * @param userId ID Discord de l'utilisateur
     * @param type   type de cooldown
     */
    public void setCooldown(long userId, CooldownType type) {
        cooldowns.put(key(userId, type), System.currentTimeMillis() + type.getDurationMs());
    }

    /**
     * Retourne le temps restant en millisecondes avant la fin du cooldown.
     * Retourne 0 si pas en cooldown.
     *
     * @param userId ID Discord de l'utilisateur
     * @param type   type de cooldown
     * @return millisecondes restantes (>= 0)
     */
    public long getRemainingMs(long userId, CooldownType type) {
        Long expires = cooldowns.get(key(userId, type));
        if (expires == null) return 0;
        long remaining = expires - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    // =========================================================================
    // Utilitaire
    // =========================================================================

    /**
     * Clé unique par (userId, type) — évite une Map imbriquée.
     * Le modulo par le nombre de types garantit l'unicité sur des IDs Discord normaux.
     */
    private long key(long userId, CooldownType type) {
        return userId * CooldownType.values().length + type.ordinal();
    }
}
