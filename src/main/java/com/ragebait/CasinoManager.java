package com.ragebait;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class CasinoManager {
    
    private static CasinoManager instance;
    private static final String CASINO_FILE = "casino.txt";
    private static final long DEFAULT_BALANCE = 1000;
    private static final long DAILY_AMOUNT = 500;
    private static final long DAILY_COOLDOWN = 24 * 60 * 60; // 24 heures en secondes
    
    private final Map<Long, Long> balances = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastDaily = new ConcurrentHashMap<>();
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
        if (!balances.containsKey(userId)) {
            balances.put(userId, DEFAULT_BALANCE);
            saveData();
        }
        return balances.get(userId);
    }
    
    public void setBalance(long userId, long amount) {
        balances.put(userId, Math.max(0, amount));
        saveData();
    }
    
    public void addBalance(long userId, long amount) {
        setBalance(userId, getBalance(userId) + amount);
    }
    
    public boolean removeBalance(long userId, long amount) {
        long current = getBalance(userId);
        if (current < amount) {
            return false;
        }
        setBalance(userId, current - amount);
        return true;
    }
    
    public boolean transfer(long fromUserId, long toUserId, long amount) {
        if (amount <= 0 || getBalance(fromUserId) < amount) {
            return false;
        }
        removeBalance(fromUserId, amount);
        addBalance(toUserId, amount);
        return true;
    }
    
    // ============ DAILY ============
    
    public DailyResult claimDaily(long userId) {
        long now = Instant.now().getEpochSecond();
        Long last = lastDaily.get(userId);
        
        if (last != null && (now - last) < DAILY_COOLDOWN) {
            long remaining = DAILY_COOLDOWN - (now - last);
            return new DailyResult(false, 0, remaining);
        }
        
        lastDaily.put(userId, now);
        addBalance(userId, DAILY_AMOUNT);
        saveData();
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
        
        // Retirer la mise
        removeBalance(userId, bet);
        
        // Générer les symboles (3x3 grid, mais on affiche juste la ligne centrale)
        String[] results = new String[3];
        for (int i = 0; i < 3; i++) {
            results[i] = SLOT_SYMBOLS[random.nextInt(SLOT_SYMBOLS.length)];
        }
        
        // Calculer les gains
        long winnings = 0;
        String message;
        
        if (results[0].equals(results[1]) && results[1].equals(results[2])) {
            // Jackpot - 3 symboles identiques
            double multiplier = SLOT_MULTIPLIERS.getOrDefault(results[0], 2.0);
            winnings = (long) (bet * multiplier);
            message = "🎉 **JACKPOT!** Triple " + results[0] + "! (x" + multiplier + ")";
        } else if (results[0].equals(results[1]) || results[1].equals(results[2]) || results[0].equals(results[2])) {
            // 2 symboles identiques
            winnings = (long) (bet * 1.5);
            message = "✨ Deux symboles identiques! (x1.5)";
        } else {
            message = "💨 Pas de chance cette fois...";
        }
        
        if (winnings > 0) {
            addBalance(userId, winnings);
        }
        
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
        
        if (won) {
            addBalance(userId, winnings);
        }
        
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
        if (bet <= 0) {
            return null;
        }
        
        long balance = getBalance(userId);
        if (balance < bet) {
            return null;
        }
        
        removeBalance(userId, bet);
        return new BlackjackGame(userId, bet, random);
    }
    
    public void finishBlackjack(BlackjackGame game, long winnings) {
        if (winnings > 0) {
            addBalance(game.getUserId(), winnings);
        }
        saveData();
    }
    
    public static class BlackjackGame {
        private final long userId;
        private final long bet;
        private final java.util.List<String> playerCards = new java.util.ArrayList<>();
        private final java.util.List<String> dealerCards = new java.util.ArrayList<>();
        private final Random random;
        private boolean playerStand = false;
        private boolean gameOver = false;
        private String result = "";
        private long winnings = 0;
        
        public BlackjackGame(long userId, long bet, Random random) {
            this.userId = userId;
            this.bet = bet;
            this.random = random;
            
            // Distribution initiale
            playerCards.add(drawCard());
            dealerCards.add(drawCard());
            playerCards.add(drawCard());
            dealerCards.add(drawCard());
            
            // Check blackjack naturel
            if (calculateHand(playerCards) == 21) {
                stand(); // Auto-stand on blackjack
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
            
            while (total > 21 && aces > 0) {
                total -= 10;
                aces--;
            }
            
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
            
            // Le dealer tire jusqu'à 17
            while (calculateHand(dealerCards) < 17) {
                dealerCards.add(drawCard());
            }
            
            int playerTotal = calculateHand(playerCards);
            int dealerTotal = calculateHand(dealerCards);
            
            gameOver = true;
            
            if (dealerTotal > 21) {
                result = "🎉 **Le dealer bust!** Tu gagnes!";
                winnings = bet * 2;
            } else if (playerTotal > dealerTotal) {
                result = "🎉 **Tu gagnes!** " + playerTotal + " vs " + dealerTotal;
                winnings = bet * 2;
            } else if (dealerTotal > playerTotal) {
                result = "💨 **Le dealer gagne!** " + dealerTotal + " vs " + playerTotal;
                winnings = 0;
            } else {
                result = "🤝 **Égalité!** Mise remboursée";
                winnings = bet;
            }
        }
        
        public long getUserId() { return userId; }
        public long getBet() { return bet; }
        public java.util.List<String> getPlayerCards() { return playerCards; }
        public java.util.List<String> getDealerCards() { return dealerCards; }
        public boolean isGameOver() { return gameOver; }
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
        return balances.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(limit)
            .toList();
    }
    
    // ============ PERSISTENCE ============
    
    public void saveData() {
        try {
            Path filePath = Paths.get(CASINO_FILE).toAbsolutePath();
            try (PrintWriter writer = new PrintWriter(new FileWriter(filePath.toFile()))) {
                for (Map.Entry<Long, Long> entry : balances.entrySet()) {
                    writer.println("balance=" + entry.getKey() + ":" + entry.getValue());
                }
                for (Map.Entry<Long, Long> entry : lastDaily.entrySet()) {
                    writer.println("daily=" + entry.getKey() + ":" + entry.getValue());
                }
                writer.flush();
            }
            System.out.println("[Casino] Sauvegardé dans: " + filePath);
        } catch (IOException e) {
            System.err.println("Erreur sauvegarde casino: " + e.getMessage());
        }
    }
    
    public void loadData() {
        Path path = Paths.get(CASINO_FILE).toAbsolutePath();
        System.out.println("[Casino] Chargement depuis: " + path);
        
        if (!Files.exists(path)) {
            System.out.println("[Casino] Aucune donnée casino trouvée.");
            return;
        }
        
        try {
            for (String line : Files.readAllLines(path)) {
                if (line.startsWith("balance=")) {
                    String[] parts = line.substring(8).split(":");
                    if (parts.length == 2) {
                        balances.put(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
                    }
                } else if (line.startsWith("daily=")) {
                    String[] parts = line.substring(6).split(":");
                    if (parts.length == 2) {
                        lastDaily.put(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
                    }
                }
            }
            System.out.println("Casino chargé! " + balances.size() + " compte(s)");
        } catch (IOException | NumberFormatException e) {
            System.err.println("Erreur chargement casino: " + e.getMessage());
        }
    }
}
