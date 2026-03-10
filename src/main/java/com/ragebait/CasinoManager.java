package com.ragebait;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CasinoManager {

    private static final Logger log = LoggerFactory.getLogger(CasinoManager.class);

    private static CasinoManager instance;
    private static final long DEFAULT_BALANCE = 1000;
    private static final long DAILY_AMOUNT = 500;
    private static final long DAILY_COOLDOWN = 24 * 60 * 60; // 24h en secondes

    private final Random random = new Random();

    // Émojis pour les slots
    private static final String[] SLOT_SYMBOLS = {"🍒", "🍋", "🍊", "🍇", "💎", "7️⃣", "🎰"};
    private static final Map<String, Double> SLOT_MULTIPLIERS = Map.of(
        "🍒", 2.0,
        "🍋", 2.5,
        "🍊", 3.0,
        "🍇", 4.0,
        "💎", 10.0,
        "7️⃣", 7.0,
        "🎰", 15.0
    );

    private CasinoManager() {}

    public static synchronized CasinoManager getInstance() {
        if (instance == null) {
            instance = new CasinoManager();
        }
        return instance;
    }

    // ============ GESTION DES SOLDES ============

    public long getBalance(long userId) {
        String sql = "SELECT balance FROM casino_balances WHERE user_id = ?";
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("balance");
                }
            }
        } catch (SQLException e) {
            log.error("[Casino] Erreur getBalance pour userId={}", userId, e);
        }
        // Utilisateur inconnu → créer avec solde par défaut
        setBalance(userId, DEFAULT_BALANCE);
        return DEFAULT_BALANCE;
    }

    public void setBalance(long userId, long amount) {
        long value = Math.max(0, amount);
        String sql = """
            INSERT INTO casino_balances (user_id, balance)
            VALUES (?, ?)
            ON CONFLICT (user_id) DO UPDATE SET balance = EXCLUDED.balance
            """;
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("[Casino] Erreur setBalance pour userId={}", userId, e);
        }
    }

    public void addBalance(long userId, long amount) {
        setBalance(userId, getBalance(userId) + amount);
    }

    public boolean removeBalance(long userId, long amount) {
        long current = getBalance(userId);
        if (current < amount) return false;
        setBalance(userId, current - amount);
        return true;
    }

    public boolean transfer(long fromUserId, long toUserId, long amount) {
        if (amount <= 0 || getBalance(fromUserId) < amount) return false;
        removeBalance(fromUserId, amount);
        addBalance(toUserId, amount);
        return true;
    }

    // ============ DAILY ============

    public DailyResult claimDaily(long userId) {
        long now = Instant.now().getEpochSecond();

        String selectSql = "SELECT last_claim FROM casino_daily WHERE user_id = ?";
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long last = rs.getLong("last_claim");
                    if ((now - last) < DAILY_COOLDOWN) {
                        return new DailyResult(false, 0, DAILY_COOLDOWN - (now - last));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("[Casino] Erreur claimDaily (lecture) pour userId={}", userId, e);
        }

        // Mettre à jour le timestamp et créditer
        String upsertSql = """
            INSERT INTO casino_daily (user_id, last_claim)
            VALUES (?, ?)
            ON CONFLICT (user_id) DO UPDATE SET last_claim = EXCLUDED.last_claim
            """;
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setLong(1, userId);
            ps.setLong(2, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("[Casino] Erreur claimDaily (upsert) pour userId={}", userId, e);
        }

        addBalance(userId, DAILY_AMOUNT);
        return new DailyResult(true, DAILY_AMOUNT, 0);
    }

    public record DailyResult(boolean success, long amount, long cooldownRemaining) {}

    // ============ SLOTS ============

    public SlotResult playSlots(long userId, long bet) {
        if (bet <= 0) {
            return new SlotResult(false, "❌ La mise doit être positive!", null, 0, 0);
        }
        long balance = getBalance(userId);
        if (balance < bet) {
            return new SlotResult(false, "❌ Tu n'as pas assez de 🪙! (Solde: " + balance + ")", null, 0, 0);
        }

        removeBalance(userId, bet);

        String[] results = new String[3];
        for (int i = 0; i < 3; i++) {
            results[i] = SLOT_SYMBOLS[random.nextInt(SLOT_SYMBOLS.length)];
        }

        long winnings = 0;
        String message;

        if (results[0].equals(results[1]) && results[1].equals(results[2])) {
            double multiplier = SLOT_MULTIPLIERS.getOrDefault(results[0], 2.0);
            winnings = (long) (bet * multiplier);
            message = "🎉 **JACKPOT!** Triple " + results[0] + "! (x" + multiplier + ")";
        } else if (results[0].equals(results[1]) || results[1].equals(results[2]) || results[0].equals(results[2])) {
            winnings = (long) (bet * 1.5);
            message = "✨ Deux symboles identiques! (x1.5)";
        } else {
            message = "💨 Pas de chance cette fois...";
        }

        if (winnings > 0) addBalance(userId, winnings);

        return new SlotResult(true, message, results, winnings, getBalance(userId));
    }

    public record SlotResult(boolean played, String message, String[] symbols, long winnings, long newBalance) {}

    // ============ COINFLIP ============

    public CoinflipResult playCoinflip(long userId, long bet, boolean chooseHeads) {
        if (bet <= 0) {
            return new CoinflipResult(false, "❌ La mise doit être positive!", false, false, 0, 0);
        }
        long balance = getBalance(userId);
        if (balance < bet) {
            return new CoinflipResult(false, "❌ Tu n'as pas assez de 🪙! (Solde: " + balance + ")", false, false, 0, 0);
        }

        removeBalance(userId, bet);

        boolean isHeads = random.nextBoolean();
        boolean won = (isHeads == chooseHeads);
        long winnings = won ? bet * 2 : 0;

        if (won) addBalance(userId, winnings);

        String message = won
            ? "🎉 **Gagné!** Tu remportes " + winnings + " 🪙!"
            : "💨 **Perdu!** La pièce a montré " + (isHeads ? "Pile" : "Face");

        return new CoinflipResult(true, message, isHeads, won, winnings, getBalance(userId));
    }

    public record CoinflipResult(boolean played, String message, boolean wasHeads, boolean won, long winnings, long newBalance) {}

    // ============ BLACKJACK ============

    private static final String[] CARDS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
    private static final String[] SUITS = {"♠", "♥", "♦", "♣"};

    public BlackjackGame startBlackjack(long userId, long bet) {
        if (bet <= 0) return null;
        long balance = getBalance(userId);
        if (balance < bet) return null;
        removeBalance(userId, bet);
        return new BlackjackGame(userId, bet, random);
    }

    public void finishBlackjack(BlackjackGame game, long winnings) {
        if (winnings > 0) {
            addBalance(game.getUserId(), winnings);
        }
    }

    public static class BlackjackGame {
        private final long userId;
        private final long bet;
        private final java.util.List<String> playerCards = new java.util.ArrayList<>();
        private final java.util.List<String> dealerCards = new java.util.ArrayList<>();
        private final Random random;
        private boolean playerStand = false;
        private boolean gameOver = false;
        private boolean naturalBlackjack = false;
        private String result = "";
        private long winnings = 0;

        public BlackjackGame(long userId, long bet, Random random) {
            this.userId = userId;
            this.bet = bet;
            this.random = random;

            playerCards.add(drawCard());
            dealerCards.add(drawCard());
            playerCards.add(drawCard());
            dealerCards.add(drawCard());

            if (calculateHand(playerCards) == 21) {
                naturalBlackjack = true;
                stand();
            }
        }

        private String drawCard() {
            return CARDS[random.nextInt(CARDS.length)] + SUITS[random.nextInt(SUITS.length)];
        }

        public static int calculateHand(java.util.List<String> cards) {
            int total = 0;
            int aces = 0;
            for (String card : cards) {
                String value = card.substring(0, card.length() - 1);
                switch (value) {
                    case "A" -> { aces++; total += 11; }
                    case "K", "Q", "J" -> total += 10;
                    default -> total += Integer.parseInt(value);
                }
            }
            while (total > 21 && aces > 0) { total -= 10; aces--; }
            return total;
        }

        public void hit() {
            if (gameOver) return;
            playerCards.add(drawCard());
            int playerTotal = calculateHand(playerCards);
            if (playerTotal > 21) {
                gameOver = true;
                result = "💥 **BUST!** Tu as dépassé 21!";
                winnings = 0;
            } else if (playerTotal == 21) {
                stand();
            }
        }

        public void stand() {
            if (gameOver) return;
            playerStand = true;
            while (calculateHand(dealerCards) < 17) dealerCards.add(drawCard());

            int playerTotal = calculateHand(playerCards);
            int dealerTotal = calculateHand(dealerCards);
            gameOver = true;

            if (dealerTotal > 21) {
                if (naturalBlackjack) {
                    result = "🎉 **BLACKJACK NATUREL!** 21 au premier tirage!";
                    winnings = (long) (bet * 2.5);
                } else {
                    result = "🎉 **Le dealer bust!** Tu gagnes!";
                    winnings = bet * 2;
                }
            } else if (playerTotal > dealerTotal) {
                if (naturalBlackjack) {
                    result = "🎉 **BLACKJACK NATUREL!** 21 au premier tirage!";
                    winnings = (long) (bet * 2.5);
                } else {
                    result = "🎉 **Tu gagnes!** " + playerTotal + " vs " + dealerTotal;
                    winnings = bet * 2;
                }
            } else if (dealerTotal > playerTotal) {
                result = "💨 **Le dealer gagne!** " + dealerTotal + " vs " + playerTotal;
                winnings = 0;
            } else {
                if (naturalBlackjack && dealerTotal == 21 && dealerCards.size() > 2) {
                    result = "🎉 **BLACKJACK NATUREL!** Tu bats le 21 du dealer!";
                    winnings = (long) (bet * 2.5);
                } else {
                    result = "🤝 **Égalité!** Mise remboursée";
                    winnings = bet;
                }
            }
        }

        public long getUserId() { return userId; }
        public long getBet() { return bet; }
        public java.util.List<String> getPlayerCards() { return playerCards; }
        public java.util.List<String> getDealerCards() { return dealerCards; }
        public boolean isGameOver() { return gameOver; }
        public boolean isNaturalBlackjack() { return naturalBlackjack; }
        public String getResult() { return result; }
        public long getWinnings() { return winnings; }

        public String getPlayerHand() {
            return String.join(" ", playerCards) + " (" + calculateHand(playerCards) + ")";
        }

        public String getDealerHand(boolean reveal) {
            if (reveal || gameOver) {
                return String.join(" ", dealerCards) + " (" + calculateHand(dealerCards) + ")";
            }
            return dealerCards.get(0) + " 🂠";
        }
    }

    // ============ LEADERBOARD ============

    public java.util.List<Map.Entry<Long, Long>> getLeaderboard(int limit) {
        List<Map.Entry<Long, Long>> entries = new ArrayList<>();
        String sql = "SELECT user_id, balance FROM casino_balances ORDER BY balance DESC LIMIT ?";
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long uid = rs.getLong("user_id");
                    long bal = rs.getLong("balance");
                    entries.add(Map.entry(uid, bal));
                }
            }
        } catch (SQLException e) {
            log.error("[Casino] Erreur getLeaderboard", e);
        }
        return entries;
    }

    // ============ UTILITAIRE ============

    private DatabaseManager db() {
        return DatabaseManager.getInstance();
    }
}
