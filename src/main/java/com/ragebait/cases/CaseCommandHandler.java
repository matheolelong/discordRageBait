package com.ragebait.cases;

import com.ragebait.CasinoManager;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Logique metier des commandes textuelles de caisse.
 *
 * <p>Commandes gerees (appel depuis SlashCommandListener) :</p>
 * <ul>
 *   <li>/inventory [user] – inventaire unifie caisses + armes avec boutons</li>
 *   <li>/cases – shop interactif avec boutons Buy</li>
 *   <li>/opencase  – ouverture avec animation (aussi appelee par CaseInteractionHandler)</li>
 *   <li>/weapons   – liste detaillee des armes</li>
 *   <li>/sell      – vente individuelle d'une arme</li>
 * </ul>
 */
public class CaseCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(CaseCommandHandler.class);

    private static final int ANIMATION_DELAY_MS = 500;
    private static final int ANIMATION_FRAMES   = 8;
    private static final int REEL_SIZE          = 5;

    private final Random          random        = new Random();
    private final DropSystem      dropSystem    = new DropSystem(random);
    private final FloatCalculator floatCalc     = new FloatCalculator(random);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "case-animation");
        t.setDaemon(true);
        return t;
    });

    // =========================================================================
    // /inventory [user]
    // =========================================================================

    /**
     * Affiche l'inventaire unife caisses + armes avec boutons interactifs.
     *
     * @param hook       hook de la slash command (deja deferred)
     * @param viewerId   ID Discord du joueur qui fait la commande
     * @param targetUser joueur cible (peut etre le joueur lui-meme ou un autre)
     */
    public void handleInventory(InteractionHook hook, long viewerId, User targetUser) {
        boolean isSelf = targetUser.getIdLong() == viewerId;
        long targetId = targetUser.getIdLong();

        CaseManager cm = CaseManager.getInstance();
        CasinoManager casino = CasinoManager.getInstance();

        Map<String, Integer> cases   = cm.getFullCaseInventory(targetId);
        List<WeaponDrop>     weapons = cm.getWeaponInventory(targetId);
        long balance = casino.getBalance(targetId);
        String name   = targetUser.getName();
        String avatar = targetUser.getEffectiveAvatarUrl();

        if (isSelf) {
            // Vue interactive avec boutons
            hook.sendMessageEmbeds(InventoryUI.buildMainEmbed(name, avatar, cases, weapons, balance))
                    .setComponents(InventoryUI.buildMainButtons(targetId, cases, weapons))
                    .queue();
        } else {
            // Vue lecture seule (pas de boutons)
            hook.sendMessageEmbeds(InventoryUI.buildReadOnlyEmbed(name, avatar, cases, weapons, balance))
                    .queue();
        }
    }

    // =========================================================================
    // /cases – shop interactif
    // =========================================================================

    /**
     * Affiche le shop avec un bouton Buy sous chaque caisse.
     *
     * @param hook    hook de la slash command (deja deferred)
     * @param userId  ID Discord du joueur
     */
    public void handleShop(InteractionHook hook, long userId) {
        Collection<Case> cases = CaseManager.getInstance().getAllCases();
        long balance = CasinoManager.getInstance().getBalance(userId);

        if (cases.isEmpty()) {
            hook.sendMessage("📦 Aucune caisse disponible. Configurez `cases/cases.json` et redemarrez le bot.")
                    .queue();
            return;
        }

        hook.sendMessageEmbeds(ShopUI.buildShopEmbed(cases, balance, null))
                .setComponents(ShopUI.buildShopButtons(userId, cases, balance))
                .queue();
    }

    // =========================================================================
    // /opencase <caseName> – ouverture avec animation + stockage arme en BDD
    // =========================================================================

    /**
     * Ouvre une caisse : animation de defilement puis stockage de l'arme droppee.
     * Peut etre appele depuis SlashCommandListener (/opencase) ou CaseInteractionHandler (bouton).
     *
     * @param hook      hook de la slash command ou de l'interaction bouton
     * @param userId    ID Discord du joueur
     * @param mention   mention Discord du joueur (pour le message de resultat)
     * @param caseName  nom de la caisse a ouvrir
     */
    public void handleOpenCase(InteractionHook hook, long userId, String mention, String caseName) {
        CaseManager cm = CaseManager.getInstance();

        Case targetCase = cm.getCase(caseName);
        if (targetCase == null) {
            hook.sendMessage("❌ Caisse introuvable : **" + caseName + "**\nUtilise `/cases` pour voir les caisses.")
                    .setEphemeral(true).queue();
            return;
        }

        if (cm.getInventoryCount(userId, caseName) <= 0) {
            hook.sendMessage("❌ Tu ne possedes pas la caisse **" + caseName + "**!\nUtilise `/cases` pour en acheter.")
                    .setEphemeral(true).queue();
            return;
        }

        // Retirer la caisse
        cm.removeFromInventory(userId, caseName);

        // Calculer le drop
        Weapon dropped    = dropSystem.roll(targetCase.getWeapons());
        double floatValue = floatCalc.generateFloat(dropped);
        String quality    = floatCalc.getQuality(floatValue);
        String qualShort  = floatCalc.getQualityShort(floatValue);
        long finalPrice   = floatCalc.calculatePrice(dropped, floatValue);

        // Stocker l'arme en BDD
        int weaponId = cm.addWeaponDrop(userId, dropped.getName(), caseName, qualShort, floatValue, finalPrice);

        // Lancer l'animation
        launchAnimation(hook, targetCase, mention, dropped, floatValue, quality, finalPrice, weaponId);

        log.info("[Cases] {} a ouvert '{}' -> {} ({} float={} prix={} wid={})",
                userId, caseName, dropped.getName(), qualShort, floatValue, finalPrice, weaponId);
    }

    private void launchAnimation(InteractionHook hook, Case targetCase, String mention,
                                  Weapon dropped, double floatValue, String quality,
                                  long finalPrice, int weaponId) {

        Weapon[] reel = dropSystem.generateAnimationReel(
                targetCase.getWeapons(), ANIMATION_FRAMES * REEL_SIZE + REEL_SIZE);

        hook.sendMessage(buildOpeningMsg(targetCase.getName())).queue(message -> {
            for (int frame = 0; frame < ANIMATION_FRAMES; frame++) {
                final int f = frame;
                scheduler.schedule(() -> {
                    String line = buildAnimLine(reel, f * REEL_SIZE, REEL_SIZE);
                    message.editMessage(buildAnimFrame(targetCase.getName(), line, f + 1, ANIMATION_FRAMES)).queue();
                }, (long) (f + 1) * ANIMATION_DELAY_MS, TimeUnit.MILLISECONDS);
            }
            scheduler.schedule(() -> {
                message.editMessage(
                        buildResultMsg(mention, targetCase.getName(), dropped, floatValue, quality, finalPrice, weaponId)
                ).queue();
            }, (long) (ANIMATION_FRAMES + 1) * ANIMATION_DELAY_MS, TimeUnit.MILLISECONDS);
        });
    }

    // =========================================================================
    // /weapons – liste detaillee des armes
    // =========================================================================

    public void handleWeapons(InteractionHook hook, long userId, String userName) {
        List<WeaponDrop> weapons = CaseManager.getInstance().getWeaponInventory(userId);

        if (weapons.isEmpty()) {
            hook.sendMessage("🔫 Tu n'as aucune arme. Ouvre des caisses avec `/opencase <nom>` ou via `/inventory`!").queue();
            return;
        }

        long total = weapons.stream().mapToLong(WeaponDrop::getPrice).sum();
        StringBuilder sb = new StringBuilder("🔫 **Armes de ").append(userName)
                .append("** — ").append(weapons.size()).append(" arme(s) | Valeur : **").append(total).append(" 🪙**\n\n");

        for (WeaponDrop w : weapons) {
            sb.append("`#").append(w.getId()).append("` **").append(w.getWeaponName()).append("**")
              .append(" | ").append(InventoryUI.qualityEmoji(w.getQuality())).append(" ").append(w.getQuality())
              .append(" | `").append(String.format("%.4f", w.getFloatValue())).append("`")
              .append(" | **").append(w.getPrice()).append("** 🪙")
              .append(" — `/sell id:").append(w.getId()).append("`\n");
        }
        hook.sendMessage(sb.toString()).queue();
    }

    // =========================================================================
    // /sell id:<n> – vente individuelle
    // =========================================================================

    public void handleSell(InteractionHook hook, long userId, int weaponId) {
        CaseManager cm = CaseManager.getInstance();
        WeaponDrop weapon = cm.getWeaponById(weaponId, userId);

        if (weapon == null) {
            hook.sendMessage("❌ Aucune arme **#" + weaponId + "** dans ton inventaire.\nUtilise `/weapons` pour voir tes IDs.")
                    .setEphemeral(true).queue();
            return;
        }

        cm.removeWeapon(weaponId, userId);
        CasinoManager.getInstance().addBalance(userId, weapon.getPrice());
        long newBalance = CasinoManager.getInstance().getBalance(userId);

        hook.sendMessage("💸 **Arme vendue!**\n\n" +
                "🔫 **" + weapon.getWeaponName() + "**" +
                " | " + InventoryUI.qualityEmoji(weapon.getQuality()) + " " + weapon.getQuality() +
                " | `" + String.format("%.4f", weapon.getFloatValue()) + "`\n\n" +
                "💰 Gain : **+" + weapon.getPrice() + "** 🪙\n" +
                "💰 Nouveau solde : **" + newBalance + "** 🪙").queue();

        log.info("[Cases] {} a vendu l'arme #{} ({}) pour {} coins.", userId, weaponId, weapon.getWeaponName(), weapon.getPrice());
    }

    // =========================================================================
    // Formatage des messages d'animation
    // =========================================================================

    private String buildOpeningMsg(String caseName) {
        return "🎁 **Ouverture de la caisse** `" + caseName + "`...\n```\n[ Preparation... ]\n```";
    }

    private String buildAnimFrame(String caseName, String line, int frame, int total) {
        return "🎰 **Ouverture de** `" + caseName + "`\n```\n" + line + "\n```\n" + buildProgressBar(frame, total);
    }

    private String buildAnimLine(Weapon[] reel, int offset, int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            Weapon w = reel[Math.min(offset + i, reel.length - 1)];
            String name = w.getName().length() > 12 ? w.getName().substring(0, 12) + "~" : w.getName();
            sb.append(i == size / 2 ? "> " + name + " <" : "  " + name + "  ");
            if (i < size - 1) sb.append(" | ");
        }
        return sb.toString();
    }

    private String buildProgressBar(int current, int total) {
        int filled = (int) ((double) current / total * 10);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < 10; i++) bar.append(i < filled ? "#" : "-");
        return bar.append("] ").append(current * 10).append("%").toString();
    }

    private String buildResultMsg(String mention, String caseName, Weapon weapon,
                                   double floatValue, String quality, long price, int weaponId) {
        return "🎁 **" + mention + " a ouvert** `" + caseName + "` !\n\n" +
               "━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
               "🔫 **" + weapon.getName() + "**\n" +
               "🎨 Condition : " + quality + "\n" +
               "📊 Float : `" + String.format("%.4f", floatValue) + "`\n" +
               "💰 Valeur : **" + price + "** 🪙\n\n" +
               "📦 Arme stockee dans ton inventaire (ID **#" + weaponId + "**)\n" +
               "➡️ `/inventory` | `/sell id:" + weaponId + "`\n" +
               "━━━━━━━━━━━━━━━━━━━━━━━━━━━";
    }
}
