package com.ragebait.cases;

import com.ragebait.CasinoManager;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Logique metier des commandes de caisse, utilisee par SlashCommandListener.
 *
 * <p>Commandes gerees :</p>
 * <ul>
 *   <li>/cases     – liste des caisses disponibles a l'achat</li>
 *   <li>/buycase   – achat d'une caisse</li>
 *   <li>/opencase  – ouverture avec animation, l'arme est stockee en BDD</li>
 *   <li>/inventory – inventaire de caisses du joueur</li>
 *   <li>/weapons   – inventaire d'armes droppees du joueur</li>
 *   <li>/sell      – vente d'une arme contre des coins</li>
 * </ul>
 */
public class CaseCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(CaseCommandHandler.class);

    private static final int ANIMATION_DELAY_MS = 500;
    private static final int ANIMATION_FRAMES   = 8;
    private static final int REEL_SIZE          = 5;

    private final Random random = new Random();
    private final DropSystem dropSystem = new DropSystem(random);
    private final FloatCalculator floatCalculator = new FloatCalculator(random);

    /** Executor pour les animations asynchrones */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "case-animation");
        t.setDaemon(true);
        return t;
    });

    // =========================================================================
    // /cases – liste des caisses disponibles
    // =========================================================================

    public void handleListCases(InteractionHook hook) {
        Collection<Case> cases = CaseManager.getInstance().getAllCases();

        if (cases.isEmpty()) {
            hook.sendMessage("📦 Aucune caisse disponible. Configurez `cases/cases.json` et redemarrez le bot.").queue();
            return;
        }

        StringBuilder sb = new StringBuilder("📦 **CAISSES DISPONIBLES**\n\n");
        for (Case c : cases) {
            sb.append("🎁 **").append(c.getName()).append("**")
              .append(" — **").append(c.getPrice()).append("** 🪙")
              .append(" (").append(c.getWeapons().size()).append(" arme(s))\n")
              .append("   ↳ `/buycase ").append(c.getName()).append("`\n");
        }
        hook.sendMessage(sb.toString()).queue();
    }

    // =========================================================================
    // /buycase <caseName> – achat d'une caisse
    // =========================================================================

    public void handleBuyCase(InteractionHook hook, long userId, String mention, String caseName) {
        CaseManager caseManager = CaseManager.getInstance();
        CasinoManager casino = CasinoManager.getInstance();

        Case targetCase = caseManager.getCase(caseName);
        if (targetCase == null) {
            hook.sendMessage("❌ Caisse introuvable : **" + caseName + "**\nUtilise `/cases` pour voir les caisses disponibles.")
                .setEphemeral(true).queue();
            return;
        }

        long balance = casino.getBalance(userId);
        if (balance < targetCase.getPrice()) {
            hook.sendMessage(
                    "❌ Tu n'as pas assez de 🪙 pour acheter **" + targetCase.getName() + "**!\n" +
                    "💰 Ton solde : **" + balance + "** 🪙 | 💳 Prix : **" + targetCase.getPrice() + "** 🪙")
                .setEphemeral(true).queue();
            return;
        }

        casino.removeBalance(userId, targetCase.getPrice());
        caseManager.addToInventory(userId, targetCase.getName(), 1);

        long newBalance = casino.getBalance(userId);
        hook.sendMessage(
                "✅ Tu as acheté la caisse **" + targetCase.getName() + "** pour **" + targetCase.getPrice() + "** 🪙!\n" +
                "💰 Nouveau solde : **" + newBalance + "** 🪙\n" +
                "🎁 Utilise `/opencase " + targetCase.getName() + "` pour l'ouvrir!").queue();

        log.info("[Cases] {} a achete '{}' pour {} coins.", userId, targetCase.getName(), targetCase.getPrice());
    }

    // =========================================================================
    // /opencase <caseName> – ouverture avec animation + stockage arme en BDD
    // =========================================================================

    public void handleOpenCase(InteractionHook hook, long userId, String mention, String caseName) {
        CaseManager caseManager = CaseManager.getInstance();

        Case targetCase = caseManager.getCase(caseName);
        if (targetCase == null) {
            hook.sendMessage("❌ Caisse introuvable : **" + caseName + "**\nUtilise `/cases` pour voir les caisses disponibles.")
                .setEphemeral(true).queue();
            return;
        }

        int qty = caseManager.getInventoryCount(userId, targetCase.getName());
        if (qty <= 0) {
            hook.sendMessage(
                    "❌ Tu ne possedes pas la caisse **" + targetCase.getName() + "**!\n" +
                    "Utilise `/buycase " + targetCase.getName() + "` pour l'acheter.")
                .setEphemeral(true).queue();
            return;
        }

        // Retirer la caisse de l'inventaire
        caseManager.removeFromInventory(userId, targetCase.getName());

        // Determiner le drop
        Weapon dropped = dropSystem.roll(targetCase.getWeapons());
        double floatValue = floatCalculator.generateFloat(dropped);
        String quality = floatCalculator.getQuality(floatValue);
        String qualityShort = floatCalculator.getQualityShort(floatValue);
        long finalPrice = floatCalculator.calculatePrice(dropped, floatValue);

        // Stocker l'arme dans l'inventaire BDD du joueur
        int weaponId = caseManager.addWeaponDrop(
                userId, dropped.getName(), targetCase.getName(), qualityShort, floatValue, finalPrice);

        // Lancer l'animation
        launchAnimation(hook, targetCase, mention, dropped, floatValue, quality, qualityShort, finalPrice, weaponId);

        log.info("[Cases] {} a ouvert '{}' -> {} ({}, float={}, prix={}, weaponId={})",
                userId, targetCase.getName(), dropped.getName(), qualityShort, floatValue, finalPrice, weaponId);
    }

    private void launchAnimation(InteractionHook hook, Case targetCase, String mention,
                                  Weapon dropped, double floatValue, String quality,
                                  String qualityShort, long finalPrice, int weaponId) {

        Weapon[] reel = dropSystem.generateAnimationReel(targetCase.getWeapons(),
                ANIMATION_FRAMES * REEL_SIZE + REEL_SIZE);

        hook.sendMessage(buildOpeningMessage(targetCase.getName())).queue(message -> {
            for (int frame = 0; frame < ANIMATION_FRAMES; frame++) {
                final int f = frame;
                scheduler.schedule(() -> {
                    String animLine = buildAnimationLine(reel, f * REEL_SIZE, REEL_SIZE);
                    message.editMessage(buildAnimationFrame(targetCase.getName(), animLine, f + 1, ANIMATION_FRAMES)).queue();
                }, (long) (f + 1) * ANIMATION_DELAY_MS, TimeUnit.MILLISECONDS);
            }

            scheduler.schedule(() -> {
                String resultMsg = buildResultMessage(
                        mention, targetCase.getName(), dropped, floatValue, quality, finalPrice, weaponId);
                message.editMessage(resultMsg).queue();
            }, (long) (ANIMATION_FRAMES + 1) * ANIMATION_DELAY_MS, TimeUnit.MILLISECONDS);
        });
    }

    // =========================================================================
    // /inventory – liste des caisses possedees
    // =========================================================================

    public void handleCaseInventory(InteractionHook hook, long userId, String userName) {
        Map<String, Integer> inventory = CaseManager.getInstance().getFullCaseInventory(userId);

        if (inventory.isEmpty()) {
            hook.sendMessage(
                    "📦 Tu n'as aucune caisse dans ton inventaire.\n" +
                    "Utilise `/cases` pour voir les caisses et `/buycase <nom>` pour en acheter!").queue();
            return;
        }

        StringBuilder sb = new StringBuilder("📦 **Caisses de ").append(userName).append("**\n\n");
        for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
            sb.append("🎁 **").append(entry.getKey()).append("** x").append(entry.getValue()).append("\n")
              .append("   ↳ `/opencase ").append(entry.getKey()).append("`\n");
        }
        hook.sendMessage(sb.toString()).queue();
    }

    // =========================================================================
    // /weapons – inventaire des armes droppees
    // =========================================================================

    public void handleWeapons(InteractionHook hook, long userId, String userName) {
        List<WeaponDrop> weapons = CaseManager.getInstance().getWeaponInventory(userId);

        if (weapons.isEmpty()) {
            hook.sendMessage(
                    "🔫 Tu n'as aucune arme dans ton inventaire.\n" +
                    "Ouvre des caisses avec `/opencase <nom>` pour en obtenir!").queue();
            return;
        }

        StringBuilder sb = new StringBuilder("🔫 **Armes de ").append(userName)
                .append("** (").append(weapons.size()).append(" arme(s))\n\n");

        long totalValue = 0;
        for (WeaponDrop w : weapons) {
            totalValue += w.getPrice();
            sb.append("**`#").append(w.getId()).append("`** | **").append(w.getWeaponName()).append("**")
              .append(" | ").append(qualityEmoji(w.getQuality())).append(" ").append(w.getQuality())
              .append(" | Float: `").append(String.format("%.4f", w.getFloatValue())).append("`")
              .append(" | **").append(w.getPrice()).append("** 🪙")
              .append("  →  `/sell id:").append(w.getId()).append("`\n");
        }

        sb.append("\n💰 **Valeur totale : ").append(totalValue).append(" 🪙**");
        hook.sendMessage(sb.toString()).queue();
    }

    // =========================================================================
    // /sell id:<n> – vente d'une arme
    // =========================================================================

    public void handleSell(InteractionHook hook, long userId, int weaponId) {
        CaseManager caseManager = CaseManager.getInstance();

        // Verifier que l'arme appartient bien au joueur
        WeaponDrop weapon = caseManager.getWeaponById(weaponId, userId);
        if (weapon == null) {
            hook.sendMessage(
                    "❌ Aucune arme avec l'ID **#" + weaponId + "** dans ton inventaire.\n" +
                    "Utilise `/weapons` pour voir tes armes et leurs IDs.")
                .setEphemeral(true).queue();
            return;
        }

        // Supprimer l'arme et crediter le prix
        boolean removed = caseManager.removeWeapon(weaponId, userId);
        if (!removed) {
            hook.sendMessage("❌ Impossible de vendre cette arme. Reessaie.").setEphemeral(true).queue();
            return;
        }

        CasinoManager.getInstance().addBalance(userId, weapon.getPrice());
        long newBalance = CasinoManager.getInstance().getBalance(userId);

        hook.sendMessage(
                "💸 **Arme vendue !**\n\n" +
                "🔫 **" + weapon.getWeaponName() + "**" +
                " | " + qualityEmoji(weapon.getQuality()) + " " + weapon.getQuality() +
                " | Float: `" + String.format("%.4f", weapon.getFloatValue()) + "`\n\n" +
                "💰 Gain : **+" + weapon.getPrice() + "** 🪙\n" +
                "💰 Nouveau solde : **" + newBalance + "** 🪙").queue();

        log.info("[Cases] {} a vendu l'arme #{} ({}) pour {} coins.", userId, weaponId, weapon.getWeaponName(), weapon.getPrice());
    }

    // =========================================================================
    // Formatage des messages
    // =========================================================================

    private String buildOpeningMessage(String caseName) {
        return "🎁 **Ouverture de la caisse** `" + caseName + "`...\n" +
               "```\n[ Preparation... ]\n```";
    }

    private String buildAnimationFrame(String caseName, String animLine, int frame, int total) {
        return "🎰 **Ouverture de** `" + caseName + "`\n" +
               "```\n" + animLine + "\n```\n" +
               buildProgressBar(frame, total);
    }

    private String buildAnimationLine(Weapon[] reel, int offset, int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            Weapon w = reel[Math.min(offset + i, reel.length - 1)];
            String name = w.getName();
            if (name.length() > 12) name = name.substring(0, 12) + "~";
            sb.append(i == size / 2 ? "> " + name + " <" : "  " + name + "  ");
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
                                       double floatValue, String quality, long finalPrice, int weaponId) {
        return "🎁 **" + mention + " a ouvert** `" + caseName + "` !\n\n" +
               "━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
               "🔫 **" + weapon.getName() + "**\n" +
               "🎨 Condition : " + quality + "\n" +
               "📊 Float : `" + String.format("%.4f", floatValue) + "`\n" +
               "💰 Valeur : **" + finalPrice + "** 🪙\n\n" +
               "📦 Arme stockee dans ton inventaire! (ID **#" + weaponId + "**)\n" +
               "➡️ `/weapons` pour voir | `/sell id:" + weaponId + "` pour vendre\n" +
               "━━━━━━━━━━━━━━━━━━━━━━━━━━━";
    }

    /** Emoji associe a un code qualite court (FN, MW, FT, WW, BS). */
    private String qualityEmoji(String quality) {
        return switch (quality) {
            case "FN" -> "✨";
            case "MW" -> "🟢";
            case "FT" -> "🔵";
            case "WW" -> "🟡";
            case "BS" -> "🔴";
            default   -> "⚪";
        };
    }
}
