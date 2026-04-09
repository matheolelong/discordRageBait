package com.ragebait.cases;

import com.ragebait.CasinoManager;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Logique metier des commandes de caisse, utilisee par SlashCommandListener.
 *
 * <p>Toutes les methodes recoivent un {@link InteractionHook} (la reponse differee
 * d'une slash command). L'animation d'ouverture edite ce hook plusieurs fois.</p>
 */
public class CaseCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(CaseCommandHandler.class);

    /** Delai entre chaque frame de l'animation (ms) */
    private static final int ANIMATION_DELAY_MS = 500;

    /** Nombre de frames d'animation avant de reveler le resultat */
    private static final int ANIMATION_FRAMES = 8;

    /** Longueur du "reel" affiche a chaque frame */
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
    // /buycase <caseName>
    // =========================================================================

    /**
     * Achete une caisse pour le joueur.
     *
     * @param hook     le hook de la slash command (deja deferred)
     * @param userId   ID Discord du joueur
     * @param mention  mention formatee du joueur (pour les messages)
     * @param caseName nom de la caisse demandee
     */
    public void handleBuyCase(InteractionHook hook, long userId, String mention, String caseName) {
        CaseManager caseManager = CaseManager.getInstance();
        CasinoManager casino = CasinoManager.getInstance();

        // 1. Verifier que la caisse existe
        Case targetCase = caseManager.getCase(caseName);
        if (targetCase == null) {
            hook.sendMessage(
                    "❌ Caisse introuvable : **" + caseName + "**\n" +
                    "Utilise `/cases` pour voir les caisses disponibles."
            ).setEphemeral(true).queue();
            return;
        }

        // 2. Verifier le solde du joueur
        long balance = casino.getBalance(userId);
        if (balance < targetCase.getPrice()) {
            hook.sendMessage(
                    "❌ Tu n'as pas assez de 🪙 pour acheter **" + targetCase.getName() + "**!\n" +
                    "💰 Ton solde : **" + balance + "** 🪙 | 💳 Prix : **" + targetCase.getPrice() + "** 🪙"
            ).setEphemeral(true).queue();
            return;
        }

        // 3. Debiter le prix et ajouter a l'inventaire
        casino.removeBalance(userId, targetCase.getPrice());
        caseManager.addToInventory(userId, targetCase.getName(), 1);

        long newBalance = casino.getBalance(userId);
        hook.sendMessage(
                "✅ Tu as acheté la caisse **" + targetCase.getName() + "** pour **" + targetCase.getPrice() + "** 🪙!\n" +
                "💰 Nouveau solde : **" + newBalance + "** 🪙\n" +
                "🎁 Utilise `/opencase " + targetCase.getName() + "` pour l'ouvrir!"
        ).queue();

        log.info("[Cases] {} a achete '{}' pour {} coins.", userId, targetCase.getName(), targetCase.getPrice());
    }

    // =========================================================================
    // /opencase <caseName>
    // =========================================================================

    /**
     * Ouvre une caisse avec animation de defilement.
     *
     * @param hook     le hook de la slash command (deja deferred)
     * @param userId   ID Discord du joueur
     * @param mention  mention formatee du joueur
     * @param caseName nom de la caisse a ouvrir
     */
    public void handleOpenCase(InteractionHook hook, long userId, String mention, String caseName) {
        CaseManager caseManager = CaseManager.getInstance();

        // 1. Verifier que la caisse existe
        Case targetCase = caseManager.getCase(caseName);
        if (targetCase == null) {
            hook.sendMessage(
                    "❌ Caisse introuvable : **" + caseName + "**\n" +
                    "Utilise `/cases` pour voir les caisses disponibles."
            ).setEphemeral(true).queue();
            return;
        }

        // 2. Verifier que le joueur possede la caisse
        int qty = caseManager.getInventoryCount(userId, targetCase.getName());
        if (qty <= 0) {
            hook.sendMessage(
                    "❌ Tu ne possedes pas la caisse **" + targetCase.getName() + "**!\n" +
                    "Utilise `/buycase " + targetCase.getName() + "` pour l'acheter."
            ).setEphemeral(true).queue();
            return;
        }

        // 3. Retirer la caisse de l'inventaire
        caseManager.removeFromInventory(userId, targetCase.getName());

        // 4. Determiner le drop reel (avant l'animation)
        Weapon dropped = dropSystem.roll(targetCase.getWeapons());
        double floatValue = floatCalculator.generateFloat(dropped);
        String quality = floatCalculator.getQuality(floatValue);
        String qualityShort = floatCalculator.getQualityShort(floatValue);
        long finalPrice = floatCalculator.calculatePrice(dropped, floatValue);

        // 5. Crediter le prix de l'arme au joueur
        CasinoManager.getInstance().addBalance(userId, finalPrice);

        // 6. Lancer l'animation via le hook (editer le meme message deferred)
        launchAnimation(hook, targetCase, mention, dropped, floatValue, quality, qualityShort, finalPrice);

        log.info("[Cases] {} a ouvert '{}' -> {} ({}, float={}, prix={})",
                userId, targetCase.getName(), dropped.getName(), qualityShort, floatValue, finalPrice);
    }

    /**
     * Lance l'animation d'ouverture en editant le message deferred plusieurs fois.
     * Utilise le ScheduledExecutorService pour ne pas bloquer le thread JDA.
     */
    private void launchAnimation(InteractionHook hook, Case targetCase, String mention,
                                  Weapon dropped, double floatValue, String quality,
                                  String qualityShort, long finalPrice) {

        // Generer un reel d'animation (armes aleatoires pour le defilement)
        Weapon[] reel = dropSystem.generateAnimationReel(targetCase.getWeapons(),
                ANIMATION_FRAMES * REEL_SIZE + REEL_SIZE);

        // Envoyer le message initial "Opening case..." via le hook
        hook.sendMessage(buildOpeningMessage(targetCase.getName())).queue(message -> {
            // Planifier chaque frame d'animation
            for (int frame = 0; frame < ANIMATION_FRAMES; frame++) {
                final int f = frame;
                scheduler.schedule(() -> {
                    String animLine = buildAnimationLine(reel, f * REEL_SIZE, REEL_SIZE);
                    message.editMessage(buildAnimationFrame(targetCase.getName(), animLine, f + 1, ANIMATION_FRAMES)).queue();
                }, (long) (f + 1) * ANIMATION_DELAY_MS, TimeUnit.MILLISECONDS);
            }

            // Frame finale : afficher le resultat reel
            scheduler.schedule(() -> {
                String resultMessage = buildResultMessage(
                        mention, targetCase.getName(), dropped, floatValue, quality, finalPrice);
                message.editMessage(resultMessage).queue();
            }, (long) (ANIMATION_FRAMES + 1) * ANIMATION_DELAY_MS, TimeUnit.MILLISECONDS);
        });
    }

    // =========================================================================
    // /cases
    // =========================================================================

    /**
     * Affiche la liste des caisses disponibles.
     *
     * @param hook le hook de la slash command (deja deferred)
     */
    public void handleListCases(InteractionHook hook) {
        Collection<Case> cases = CaseManager.getInstance().getAllCases();

        if (cases.isEmpty()) {
            hook.sendMessage(
                    "📦 Aucune caisse disponible. Configurez `cases/cases.json` et redemarrez le bot."
            ).queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📦 **CAISSES DISPONIBLES**\n\n");
        for (Case c : cases) {
            sb.append("🎁 **").append(c.getName()).append("**");
            sb.append(" — **").append(c.getPrice()).append("** 🪙");
            sb.append(" (").append(c.getWeapons().size()).append(" arme(s))\n");
            sb.append("   ↳ `/buycase ").append(c.getName()).append("`\n");
        }

        hook.sendMessage(sb.toString()).queue();
    }

    // =========================================================================
    // /inventory
    // =========================================================================

    /**
     * Affiche l'inventaire de caisses du joueur.
     *
     * @param hook     le hook de la slash command (deja deferred)
     * @param userId   ID Discord du joueur
     * @param userName nom du joueur (pour le titre)
     */
    public void handleInventory(InteractionHook hook, long userId, String userName) {
        Map<String, Integer> inventory = CaseManager.getInstance().getFullInventory(userId);

        if (inventory.isEmpty()) {
            hook.sendMessage(
                    "📦 Tu n'as aucune caisse dans ton inventaire.\n" +
                    "Utilise `/cases` pour voir les caisses disponibles et `/buycase <nom>` pour en acheter!"
            ).queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📦 **Inventaire de ").append(userName).append("**\n\n");
        for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
            sb.append("🎁 **").append(entry.getKey()).append("** x").append(entry.getValue()).append("\n");
            sb.append("   ↳ `/opencase ").append(entry.getKey()).append("`\n");
        }

        hook.sendMessage(sb.toString()).queue();
    }

    // =========================================================================
    // Formatage des messages
    // =========================================================================

    private String buildOpeningMessage(String caseName) {
        return "🎁 **Ouverture de la caisse** `" + caseName + "`...\n" +
               "```\n[ Preparation... ]\n```";
    }

    private String buildAnimationFrame(String caseName, String animLine, int frame, int total) {
        String progressBar = buildProgressBar(frame, total);
        return "🎰 **Ouverture de** `" + caseName + "`\n" +
               "```\n" + animLine + "\n```\n" +
               progressBar;
    }

    private String buildAnimationLine(Weapon[] reel, int offset, int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            Weapon w = reel[Math.min(offset + i, reel.length - 1)];
            String name = w.getName();
            if (name.length() > 12) name = name.substring(0, 12) + "~";
            if (i == size / 2) {
                sb.append("> ").append(name).append(" <");
            } else {
                sb.append("  ").append(name).append("  ");
            }
            if (i < size - 1) sb.append(" | ");
        }
        return sb.toString();
    }

    private String buildProgressBar(int current, int total) {
        int filled = (int) ((double) current / total * 10);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < 10; i++) bar.append(i < filled ? "#" : "-");
        bar.append("] ").append(current * 10).append("%");
        return bar.toString();
    }

    private String buildResultMessage(String mention, String caseName, Weapon weapon,
                                       double floatValue, String quality, long finalPrice) {
        return "🎁 **" + mention + " a ouvert** `" + caseName + "` !\n\n" +
               "━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
               "🔫 **" + weapon.getName() + "**\n" +
               "🎨 Condition : " + quality + "\n" +
               "📊 Float : `" + String.format("%.4f", floatValue) + "`\n" +
               "💰 Valeur : **" + finalPrice + "** 🪙 *(crédit sur ton solde)*\n" +
               "━━━━━━━━━━━━━━━━━━━━━━━━━━━";
    }
}
