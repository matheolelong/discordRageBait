package com.ragebait.cases;

import com.ragebait.CasinoManager;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Gère les commandes de caisse en répondant aux messages textuels :
 * <ul>
 *   <li>{@code !buycase <nom>} – achète une caisse</li>
 *   <li>{@code !opencase <nom>} – ouvre une caisse avec animation</li>
 *   <li>{@code !cases} – liste des caisses disponibles</li>
 *   <li>{@code !inventory} – inventaire des caisses du joueur</li>
 * </ul>
 *
 * <p>L'animation d'ouverture édite un message plusieurs fois pour simuler
 * le défilement des armes, comme dans CS:GO.</p>
 */
public class CaseCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(CaseCommandHandler.class);

    /** Délai entre chaque frame de l'animation (ms) */
    private static final int ANIMATION_DELAY_MS = 500;

    /** Nombre de frames d'animation avant de révéler le résultat */
    private static final int ANIMATION_FRAMES = 8;

    /** Longueur du "reel" affiché à chaque frame */
    private static final int REEL_SIZE = 5;

    private final Random random = new Random();
    private final DropSystem dropSystem = new DropSystem(random);
    private final FloatCalculator floatCalculator = new FloatCalculator(random);

    /** Executor pour les animations asynchrones (ne bloque pas le thread JDA) */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "case-animation");
        t.setDaemon(true);
        return t;
    });

    // =========================================================================
    // Dispatch des commandes
    // =========================================================================

    /**
     * Point d'entrée : à appeler depuis {@code MessageListener.onMessageReceived()}.
     *
     * @param event événement de message JDA
     * @return true si la commande a été traitée, false si elle n'était pas reconnue
     */
    public boolean handle(MessageReceivedEvent event) {
        String raw = event.getMessage().getContentRaw().trim();
        String lower = raw.toLowerCase();

        if (lower.startsWith("!buycase ")) {
            String caseName = raw.substring("!buycase ".length()).trim();
            handleBuyCase(event, caseName);
            return true;
        }

        if (lower.startsWith("!opencase ")) {
            String caseName = raw.substring("!opencase ".length()).trim();
            handleOpenCase(event, caseName);
            return true;
        }

        if (lower.equals("!cases")) {
            handleListCases(event);
            return true;
        }

        if (lower.equals("!inventory") || lower.equals("!inv")) {
            handleInventory(event);
            return true;
        }

        return false;
    }

    // =========================================================================
    // !buycase <nom>
    // =========================================================================

    private void handleBuyCase(MessageReceivedEvent event, String caseName) {
        CaseManager caseManager = CaseManager.getInstance();
        CasinoManager casino = CasinoManager.getInstance();
        long userId = event.getAuthor().getIdLong();

        // 1. Vérifier que la caisse existe
        Case targetCase = caseManager.getCase(caseName);
        if (targetCase == null) {
            event.getChannel().sendMessage(
                    "❌ Caisse introuvable : **" + caseName + "**\n" +
                    "Utilise `!cases` pour voir les caisses disponibles."
            ).queue();
            return;
        }

        // 2. Vérifier le solde du joueur
        long balance = casino.getBalance(userId);
        if (balance < targetCase.getPrice()) {
            event.getChannel().sendMessage(
                    "❌ Tu n'as pas assez de 🪙 pour acheter **" + targetCase.getName() + "**!\n" +
                    "💰 Ton solde : **" + balance + "** 🪙 | 💳 Prix : **" + targetCase.getPrice() + "** 🪙"
            ).queue();
            return;
        }

        // 3. Débiter le prix et ajouter à l'inventaire
        casino.removeBalance(userId, targetCase.getPrice());
        caseManager.addToInventory(userId, targetCase.getName(), 1);

        long newBalance = casino.getBalance(userId);
        event.getChannel().sendMessage(
                "✅ Tu as acheté la caisse **" + targetCase.getName() + "** pour **" + targetCase.getPrice() + "** 🪙!\n" +
                "💰 Nouveau solde : **" + newBalance + "** 🪙\n" +
                "🎁 Utilise `!opencase " + targetCase.getName() + "` pour l'ouvrir!"
        ).queue();

        log.info("[Cases] {} a acheté '{}' pour {} coins.", userId, targetCase.getName(), targetCase.getPrice());
    }

    // =========================================================================
    // !opencase <nom>
    // =========================================================================

    private void handleOpenCase(MessageReceivedEvent event, String caseName) {
        CaseManager caseManager = CaseManager.getInstance();
        long userId = event.getAuthor().getIdLong();

        // 1. Vérifier que la caisse existe
        Case targetCase = caseManager.getCase(caseName);
        if (targetCase == null) {
            event.getChannel().sendMessage(
                    "❌ Caisse introuvable : **" + caseName + "**\n" +
                    "Utilise `!cases` pour voir les caisses disponibles."
            ).queue();
            return;
        }

        // 2. Vérifier que le joueur possède la caisse
        int qty = caseManager.getInventoryCount(userId, targetCase.getName());
        if (qty <= 0) {
            event.getChannel().sendMessage(
                    "❌ Tu ne possèdes pas la caisse **" + targetCase.getName() + "**!\n" +
                    "Utilise `!buycase " + targetCase.getName() + "` pour l'acheter."
            ).queue();
            return;
        }

        // 3. Retirer la caisse de l'inventaire
        caseManager.removeFromInventory(userId, targetCase.getName());

        // 4. Déterminer le drop réel (avant l'animation)
        Weapon dropped = dropSystem.roll(targetCase.getWeapons());
        double floatValue = floatCalculator.generateFloat(dropped);
        String quality = floatCalculator.getQuality(floatValue);
        String qualityShort = floatCalculator.getQualityShort(floatValue);
        long finalPrice = floatCalculator.calculatePrice(dropped, floatValue);

        // 5. Créditer le prix de l'arme au joueur
        CasinoManager.getInstance().addBalance(userId, finalPrice);

        // 6. Lancer l'animation asynchrone
        launchAnimation(event, targetCase, dropped, floatValue, quality, qualityShort, finalPrice);

        log.info("[Cases] {} a ouvert '{}' → {} ({}, float={}, prix={})",
                userId, targetCase.getName(), dropped.getName(), qualityShort, floatValue, finalPrice);
    }

    /**
     * Lance l'animation d'ouverture : envoie un message initial puis l'édite
     * plusieurs fois pour simuler le défilement d'armes, avant d'afficher le résultat.
     */
    private void launchAnimation(MessageReceivedEvent event, Case targetCase, Weapon dropped,
                                  double floatValue, String quality, String qualityShort, long finalPrice) {

        // Générer un reel d'animation (armes aléatoires pour le défilement)
        Weapon[] reel = dropSystem.generateAnimationReel(targetCase.getWeapons(), ANIMATION_FRAMES * REEL_SIZE + REEL_SIZE);

        // Envoyer le message initial "Opening case..."
        event.getChannel().sendMessage(buildOpeningMessage(targetCase.getName()))
                .queue(message -> {
                    // On planifie chaque frame toutes les ANIMATION_DELAY_MS ms
                    for (int frame = 0; frame < ANIMATION_FRAMES; frame++) {
                        final int f = frame;
                        scheduler.schedule(() -> {
                            // Slice du reel pour cette frame
                            String animLine = buildAnimationLine(reel, f * REEL_SIZE, REEL_SIZE);
                            message.editMessage(buildAnimationFrame(targetCase.getName(), animLine, f + 1, ANIMATION_FRAMES)).queue();
                        }, (long) (f + 1) * ANIMATION_DELAY_MS, TimeUnit.MILLISECONDS);
                    }

                    // Frame finale : afficher le résultat réel
                    scheduler.schedule(() -> {
                        String resultMessage = buildResultMessage(
                                event.getAuthor().getAsMention(),
                                targetCase.getName(),
                                dropped,
                                floatValue,
                                quality,
                                finalPrice
                        );
                        message.editMessage(resultMessage).queue();
                    }, (long) (ANIMATION_FRAMES + 1) * ANIMATION_DELAY_MS, TimeUnit.MILLISECONDS);
                });
    }

    // =========================================================================
    // !cases
    // =========================================================================

    private void handleListCases(MessageReceivedEvent event) {
        Collection<Case> cases = CaseManager.getInstance().getAllCases();

        if (cases.isEmpty()) {
            event.getChannel().sendMessage(
                    "📦 Aucune caisse disponible. Configurez `cases/cases.json` et redémarrez le bot."
            ).queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📦 **CAISSES DISPONIBLES**\n\n");
        for (Case c : cases) {
            sb.append("🎁 **").append(c.getName()).append("**");
            sb.append(" — **").append(c.getPrice()).append("** 🪙");
            sb.append(" (").append(c.getWeapons().size()).append(" arme(s))\n");
            sb.append("   ↳ `!buycase ").append(c.getName()).append("`\n");
        }

        event.getChannel().sendMessage(sb.toString()).queue();
    }

    // =========================================================================
    // !inventory
    // =========================================================================

    private void handleInventory(MessageReceivedEvent event) {
        long userId = event.getAuthor().getIdLong();
        Map<String, Integer> inventory = CaseManager.getInstance().getFullInventory(userId);

        if (inventory.isEmpty()) {
            event.getChannel().sendMessage(
                    "📦 " + event.getAuthor().getAsMention() + " n'a aucune caisse dans son inventaire.\n" +
                    "Utilise `!cases` pour voir les caisses disponibles et `!buycase <nom>` pour en acheter!"
            ).queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📦 **Inventaire de ").append(event.getAuthor().getName()).append("**\n\n");
        for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
            sb.append("🎁 **").append(entry.getKey()).append("** × ").append(entry.getValue()).append("\n");
            sb.append("   ↳ `!opencase ").append(entry.getKey()).append("`\n");
        }

        event.getChannel().sendMessage(sb.toString()).queue();
    }

    // =========================================================================
    // Formatage des messages
    // =========================================================================

    /** Message initial "en cours d'ouverture" */
    private String buildOpeningMessage(String caseName) {
        return "🎁 **Ouverture de la caisse** `" + caseName + "`...\n" +
               "```\n[ ⏳ Préparation... ]\n```";
    }

    /** Frame d'animation intermédiaire */
    private String buildAnimationFrame(String caseName, String animLine, int frame, int total) {
        String progressBar = buildProgressBar(frame, total);
        return "🎰 **Ouverture de** `" + caseName + "`\n" +
               "```\n" + animLine + "\n```\n" +
               progressBar;
    }

    /** Ligne d'armes aléatoires pour l'animation */
    private String buildAnimationLine(Weapon[] reel, int offset, int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            Weapon w = reel[Math.min(offset + i, reel.length - 1)];
            // Tronquer le nom pour l'affichage compact
            String name = w.getName();
            if (name.length() > 12) name = name.substring(0, 12) + "…";
            if (i == size / 2) {
                sb.append("► ").append(name).append(" ◄");
            } else {
                sb.append("  ").append(name).append("  ");
            }
            if (i < size - 1) sb.append(" | ");
        }
        return sb.toString();
    }

    /** Barre de progression de l'animation */
    private String buildProgressBar(int current, int total) {
        int filled = (int) ((double) current / total * 10);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < 10; i++) bar.append(i < filled ? "█" : "░");
        bar.append("] ").append(current * 10).append("%");
        return bar.toString();
    }

    /** Message final avec l'arme obtenue */
    private String buildResultMessage(String mention, String caseName, Weapon weapon,
                                       double floatValue, String quality, long finalPrice) {
        return "🎁 **" + mention + " a ouvert** `" + caseName + "` !\n\n" +
               "━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
               "🔫 **" + weapon.getName() + "**\n" +
               "🎨 Condition : " + quality + "\n" +
               "📊 Float : `" + String.format("%.4f", floatValue) + "`\n" +
               "💰 Valeur : **" + finalPrice + "** 🪙 *(crédité sur ton solde)*\n" +
               "━━━━━━━━━━━━━━━━━━━━━━━━━━━";
    }
}
