package com.ragebait;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.*;

public class RouletteManager {

    private static final Logger log = LoggerFactory.getLogger(RouletteManager.class);
    private static RouletteManager instance;
    
    private JDA jda;
    private Long channelId;
    private Long lastMessageId;
    private int delaySeconds = 30;
    private boolean running = false;
    
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> spinTask;
    private final Random random = new Random();
    
    // Paris en cours: Map<userId, List<Bet>>
    private final Map<Long, List<Bet>> currentBets = new ConcurrentHashMap<>();
    
    // Numéros rouges sur une roulette européenne
    private static final Set<Integer> RED_NUMBERS = Set.of(
        1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36
    );
    
    // Types de paris et leurs multiplicateurs
    public enum BetType {
        // Paris simples (chances simples) - x2
        RED("rouge", 2),
        BLACK("noir", 2),
        ODD("impair", 2),
        EVEN("pair", 2),
        LOW("manque", 2),      // 1-18
        HIGH("passe", 2),      // 19-36
        
        // Douzaines - x3
        DOZEN_1("douzaine1", 3),   // 1-12
        DOZEN_2("douzaine2", 3),   // 13-24
        DOZEN_3("douzaine3", 3),   // 25-36
        
        // Colonnes - x3
        COLUMN_1("colonne1", 3),   // 1,4,7,10...34
        COLUMN_2("colonne2", 3),   // 2,5,8,11...35
        COLUMN_3("colonne3", 3),   // 3,6,9,12...36
        
        // Numéro plein - x36
        STRAIGHT("numero", 36);
        
        private final String name;
        private final int multiplier;
        
        BetType(String name, int multiplier) {
            this.name = name;
            this.multiplier = multiplier;
        }
        
        public String getName() { return name; }
        public int getMultiplier() { return multiplier; }
    }
    
    public record Bet(long userId, BetType type, Integer number, long amount) {}
    
    private RouletteManager() {}
    
    public static synchronized RouletteManager getInstance() {
        if (instance == null) {
            instance = new RouletteManager();
        }
        return instance;
    }
    
    public void setJda(JDA jda) {
        this.jda = jda;
    }
    
    public void setChannel(long channelId) {
        this.channelId = channelId;
    }
    
    public Long getChannelId() {
        return channelId;
    }
    
    public void setDelay(int seconds) {
        this.delaySeconds = Math.max(10, seconds);
    }
    
    public int getDelay() {
        return delaySeconds;
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public void start() {
        if (running) {
            stop();
        }
        
        if (channelId == null || jda == null) {
            return;
        }
        
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(1);
        }
        
        running = true;
        scheduleNextSpin();
        
        // Annoncer le démarrage
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel != null) {
            EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("🎰 ROULETTE OUVERTE 🎰")
                .setDescription("La roulette tourne toutes les **" + delaySeconds + " secondes**!\n\n" +
                    "**Commandes:**\n" +
                    "`/bet <montant> <type>` - Placer un pari\n\n" +
                    "**Types de paris:**\n" +
                    "• `rouge` / `noir` - x2\n" +
                    "• `pair` / `impair` - x2\n" +
                    "• `manque` (1-18) / `passe` (19-36) - x2\n" +
                    "• `douzaine1` / `douzaine2` / `douzaine3` - x3\n" +
                    "• `colonne1` / `colonne2` / `colonne3` - x3\n" +
                    "• `0` à `36` - x36")
                .setColor(Color.GREEN);
            channel.sendMessageEmbeds(embedBuilder.build()).queue();
        }
    }
    
    public void stop() {
        running = false;
        if (spinTask != null) {
            spinTask.cancel(true);
            spinTask = null;
        }
        currentBets.clear();
        lastMessageId = null;
    }
    
    private void scheduleNextSpin() {
        if (!running || jda == null || channelId == null) {
            return;
        }
        
        try {
            spinTask = scheduler.schedule(this::spin, delaySeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("[Roulette] Erreur scheduling roulette", e);
        }
    }
    
    private void spin() {
        if (!running || jda == null || channelId == null) {
            return;
        }
        
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            scheduleNextSpin();
            return;
        }
        
        // Tirer un numéro
        int result = random.nextInt(37); // 0-36
        String color = getColor(result);
        String colorEmoji = switch (color) {
            case "rouge" -> "🔴";
            case "noir" -> "⚫";
            default -> "🟢";
        };
        
        // Calculer les gains
        CasinoManager casino = CasinoManager.getInstance();
        StringBuilder winnersText = new StringBuilder();
        StringBuilder losersText = new StringBuilder();
        int totalWinners = 0;
        int totalLosers = 0;
        long totalPayout = 0;
        
        for (Map.Entry<Long, List<Bet>> entry : currentBets.entrySet()) {
            long userId = entry.getKey();
            
            for (Bet bet : entry.getValue()) {
                boolean won = checkWin(bet, result);
                
                if (won) {
                    long winnings = bet.amount() * bet.type().getMultiplier();
                    casino.addBalance(userId, winnings);
                    totalPayout += winnings;
                    totalWinners++;
                    
                    String betDesc = bet.type() == BetType.STRAIGHT ? 
                        "n°" + bet.number() : bet.type().getName();
                    winnersText.append("<@").append(userId).append("> +**")
                        .append(winnings).append("** 🪙 (").append(betDesc).append(")\n");
                } else {
                    totalLosers++;
                    losersText.append("<@").append(userId).append("> -**")
                        .append(bet.amount()).append("** 🪙\n");
                }
            }
        }
        
        // Construire le message de résultat
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("🎰 RÉSULTAT DE LA ROULETTE 🎰")
            .setDescription("La bille s'arrête sur...\n\n# " + colorEmoji + " " + result + " " + colorEmoji)
            .setColor(color.equals("rouge") ? Color.RED : (color.equals("noir") ? Color.BLACK : Color.GREEN));
        
        // Infos supplémentaires sur le numéro
        StringBuilder numInfo = new StringBuilder();
        if (result == 0) {
            numInfo.append("🟢 Zéro");
        } else {
            numInfo.append(color.equals("rouge") ? "🔴 Rouge" : "⚫ Noir");
            numInfo.append(" | ").append(result % 2 == 0 ? "Pair" : "Impair");
            numInfo.append(" | ").append(result <= 18 ? "Manque (1-18)" : "Passe (19-36)");
            numInfo.append("\nDouzaine ").append(getDozen(result));
            numInfo.append(" | Colonne ").append(getColumn(result));
        }
        embed.addField("📊 Détails", numInfo.toString(), false);
        
        if (totalWinners > 0) {
            embed.addField("🎉 Gagnants (" + totalWinners + ")", winnersText.toString(), false);
        }
        if (totalLosers > 0 && losersText.length() < 1000) {
            embed.addField("💸 Perdants (" + totalLosers + ")", losersText.toString(), false);
        }
        
        if (currentBets.isEmpty()) {
            embed.addField("📝 Aucun pari", "Personne n'a misé ce tour!", false);
        }
        
        embed.setFooter("Prochain tirage dans " + delaySeconds + " secondes • /bet pour miser");
        
        // Supprimer le message précédent
        if (lastMessageId != null) {
            try {
                channel.deleteMessageById(lastMessageId).queue(
                    success -> {},
                    error -> {} // Ignorer si le message n'existe plus
                );
            } catch (Exception ignored) {}
        }
        
        // Envoyer le nouveau message et sauvegarder son ID
        channel.sendMessageEmbeds(embed.build()).queue(message -> {
            lastMessageId = message.getIdLong();
        });
        
        // Vider les paris
        currentBets.clear();
        
        // Planifier le prochain tour
        if (running) {
            scheduleNextSpin();
        }
    }
    
    public String placeBet(long userId, String betTypeStr, long amount) {
        if (!running) {
            return "❌ La roulette n'est pas active!";
        }
        
        CasinoManager casino = CasinoManager.getInstance();
        
        if (amount <= 0) {
            return "❌ La mise doit être positive!";
        }
        
        if (casino.getBalance(userId) < amount) {
            return "❌ Tu n'as pas assez de 🪙! (Solde: " + casino.getBalance(userId) + ")";
        }
        
        // Parser le type de pari
        betTypeStr = betTypeStr.toLowerCase().trim();
        BetType betType = null;
        Integer number = null;
        
        // Vérifier si c'est un numéro
        try {
            int num = Integer.parseInt(betTypeStr);
            if (num >= 0 && num <= 36) {
                betType = BetType.STRAIGHT;
                number = num;
            } else {
                return "❌ Numéro invalide! (0-36)";
            }
        } catch (NumberFormatException e) {
            // C'est un type de pari
            betType = switch (betTypeStr) {
                case "rouge", "red", "r" -> BetType.RED;
                case "noir", "black", "n", "b" -> BetType.BLACK;
                case "pair", "even" -> BetType.EVEN;
                case "impair", "odd" -> BetType.ODD;
                case "manque", "low", "1-18" -> BetType.LOW;
                case "passe", "high", "19-36" -> BetType.HIGH;
                case "douzaine1", "d1", "1-12" -> BetType.DOZEN_1;
                case "douzaine2", "d2", "13-24" -> BetType.DOZEN_2;
                case "douzaine3", "d3", "25-36" -> BetType.DOZEN_3;
                case "colonne1", "c1", "col1" -> BetType.COLUMN_1;
                case "colonne2", "c2", "col2" -> BetType.COLUMN_2;
                case "colonne3", "c3", "col3" -> BetType.COLUMN_3;
                default -> null;
            };
        }
        
        if (betType == null) {
            return "❌ Type de pari invalide!\n" +
                   "**Valides:** rouge, noir, pair, impair, manque, passe, " +
                   "douzaine1/2/3, colonne1/2/3, ou un numéro (0-36)";
        }
        
        // Retirer la mise
        casino.removeBalance(userId, amount);
        
        // Ajouter le pari
        Bet bet = new Bet(userId, betType, number, amount);
        currentBets.computeIfAbsent(userId, k -> new ArrayList<>()).add(bet);
        
        String betDesc = betType == BetType.STRAIGHT ? 
            "n°**" + number + "**" : "**" + betType.getName() + "**";
        
        return "✅ Pari placé: **" + amount + "** 🪙 sur " + betDesc + 
               " (x" + betType.getMultiplier() + ")\n" +
               "💰 Solde: **" + casino.getBalance(userId) + "** 🪙";
    }
    
    public String getCurrentBets(long userId) {
        List<Bet> bets = currentBets.get(userId);
        if (bets == null || bets.isEmpty()) {
            return "Tu n'as pas de paris en cours.";
        }
        
        StringBuilder sb = new StringBuilder("**Tes paris actuels:**\n");
        long total = 0;
        for (Bet bet : bets) {
            String betDesc = bet.type() == BetType.STRAIGHT ? 
                "n°" + bet.number() : bet.type().getName();
            sb.append("• **").append(bet.amount()).append("** 🪙 sur ")
              .append(betDesc).append(" (x").append(bet.type().getMultiplier()).append(")\n");
            total += bet.amount();
        }
        sb.append("\n**Total misé:** ").append(total).append(" 🪙");
        return sb.toString();
    }
    
    private boolean checkWin(Bet bet, int result) {
        return switch (bet.type()) {
            case RED -> result != 0 && RED_NUMBERS.contains(result);
            case BLACK -> result != 0 && !RED_NUMBERS.contains(result);
            case ODD -> result != 0 && result % 2 == 1;
            case EVEN -> result != 0 && result % 2 == 0;
            case LOW -> result >= 1 && result <= 18;
            case HIGH -> result >= 19 && result <= 36;
            case DOZEN_1 -> result >= 1 && result <= 12;
            case DOZEN_2 -> result >= 13 && result <= 24;
            case DOZEN_3 -> result >= 25 && result <= 36;
            case COLUMN_1 -> result != 0 && result % 3 == 1;
            case COLUMN_2 -> result != 0 && result % 3 == 2;
            case COLUMN_3 -> result != 0 && result % 3 == 0;
            case STRAIGHT -> bet.number() != null && bet.number() == result;
        };
    }
    
    private String getColor(int number) {
        if (number == 0) return "vert";
        return RED_NUMBERS.contains(number) ? "rouge" : "noir";
    }
    
    private int getDozen(int number) {
        if (number <= 12) return 1;
        if (number <= 24) return 2;
        return 3;
    }
    
    private int getColumn(int number) {
        if (number % 3 == 1) return 1;
        if (number % 3 == 2) return 2;
        return 3;
    }
    
    public void shutdown() {
        stop();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}
