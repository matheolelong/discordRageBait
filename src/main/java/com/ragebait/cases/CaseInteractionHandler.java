package com.ragebait.cases;

import com.ragebait.CasinoManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.*;

/**
 * Gere tous les clics de boutons relatifs au systeme de caisses.
 *
 * <p>Routage par prefixe d'ID :</p>
 * <ul>
 *   <li>{@code cinv:*} — interactions de l'inventaire (navigation, ouverture, vente)</li>
 *   <li>{@code cshop:*} — interactions du shop (achat de caisse)</li>
 * </ul>
 *
 * <p>Securite : chaque handler verifie que l'utilisateur qui clique est bien le proprietaire
 * (son userId est encode dans l'ID du bouton). Seule exception : les erreurs sont renvoyees
 * en ephemeral.</p>
 */
public class CaseInteractionHandler {

    private static final Logger log = LoggerFactory.getLogger(CaseInteractionHandler.class);

    /** Handler de commandes pour lancer l'animation d'ouverture de caisse. */
    private final CaseCommandHandler caseCommandHandler;

    public CaseInteractionHandler(CaseCommandHandler caseCommandHandler) {
        this.caseCommandHandler = caseCommandHandler;
    }

    /**
     * Point d'entree principal. Retourne {@code true} si le bouton a ete traite.
     */
    public boolean handle(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (id.startsWith("cinv:")) {
            handleInventoryButton(event, id);
            return true;
        }
        if (id.startsWith("cshop:")) {
            handleShopButton(event, id);
            return true;
        }
        return false;
    }

    // =========================================================================
    // Inventaire – dispatcher
    // =========================================================================

    private void handleInventoryButton(ButtonInteractionEvent event, String id) {
        // Format: cinv:<action>:<userId>[:<caseName>]
        String[] parts = id.split(":", 4);
        if (parts.length < 3) return;

        long targetUserId;
        try {
            targetUserId = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return;
        }

        // Securite : seul le proprietaire peut interagir
        if (event.getUser().getIdLong() != targetUserId) {
            event.reply("❌ Ce n'est pas ton inventaire!").setEphemeral(true).queue();
            return;
        }

        String action = parts[1];
        switch (action) {
            case "main"    -> showMainView(event, targetUserId);
            case "cases"   -> showCasesTab(event, targetUserId);
            case "weapons" -> showWeaponsTab(event, targetUserId);
            case "open"    -> {
                if (parts.length >= 4) openCase(event, targetUserId, parts[3]);
                else event.reply("❌ Donnees de bouton invalides.").setEphemeral(true).queue();
            }
            case "sell"    -> initSellAll(event, targetUserId);
            case "sellc"   -> confirmSellAll(event, targetUserId);
            case "selln"   -> showMainView(event, targetUserId);   // Cancel -> retour
            default -> event.reply("❌ Action inconnue.").setEphemeral(true).queue();
        }
    }

    // =========================================================================
    // Inventaire – vues
    // =========================================================================

    /** Retour a la vue principale de l'inventaire. */
    private void showMainView(ButtonInteractionEvent event, long userId) {
        event.deferEdit().queue();

        CaseManager cm = CaseManager.getInstance();
        CasinoManager casino = CasinoManager.getInstance();

        Map<String, Integer> cases   = cm.getFullCaseInventory(userId);
        List<WeaponDrop>     weapons = cm.getWeaponInventory(userId);
        long balance = casino.getBalance(userId);
        String name   = event.getUser().getName();
        String avatar = event.getUser().getEffectiveAvatarUrl();

        event.getHook()
                .editOriginalEmbeds(InventoryUI.buildMainEmbed(name, avatar, cases, weapons, balance))
                .setComponents(InventoryUI.buildMainButtons(userId, cases, weapons))
                .queue();
    }

    /** Onglet caisses : liste des caisses + boutons Ouvrir. */
    private void showCasesTab(ButtonInteractionEvent event, long userId) {
        event.deferEdit().queue();

        Map<String, Integer> cases = CaseManager.getInstance().getFullCaseInventory(userId);

        event.getHook()
                .editOriginalEmbeds(InventoryUI.buildCasesEmbed(event.getUser().getName(), cases))
                .setComponents(InventoryUI.buildCasesButtons(userId, cases))
                .queue();
    }

    /** Onglet armes : liste des armes + bouton Vendre Tout. */
    private void showWeaponsTab(ButtonInteractionEvent event, long userId) {
        event.deferEdit().queue();

        List<WeaponDrop> weapons = CaseManager.getInstance().getWeaponInventory(userId);

        event.getHook()
                .editOriginalEmbeds(InventoryUI.buildWeaponsEmbed(event.getUser().getName(), weapons))
                .setComponents(InventoryUI.buildWeaponsButtons(userId, !weapons.isEmpty()))
                .queue();
    }

    // =========================================================================
    // Inventaire – actions
    // =========================================================================

    /**
     * Lance l'animation d'ouverture de caisse a partir d'un bouton.
     * Envoie une nouvelle reponse (la vue inventaire reste inchangee).
     */
    private void openCase(ButtonInteractionEvent event, long userId, String caseName) {
        // Verifier AVANT de defer
        CaseManager cm = CaseManager.getInstance();
        Case targetCase = cm.getCase(caseName);
        if (targetCase == null) {
            event.reply("❌ Caisse introuvable : **" + caseName + "**")
                    .setEphemeral(true).queue();
            return;
        }
        if (cm.getInventoryCount(userId, caseName) <= 0) {
            event.reply("❌ Tu ne possedes plus la caisse **" + caseName + "**!")
                    .setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();
        caseCommandHandler.handleOpenCase(
                event.getHook(),
                userId,
                event.getUser().getAsMention(),
                caseName
        );
    }

    /**
     * Initie la vente totale : remplace l'embed par la demande de confirmation.
     * Seules les armes non verrouillee sont vendues.
     */
    private void initSellAll(ButtonInteractionEvent event, long userId) {
        CaseManager cm = CaseManager.getInstance();
        List<WeaponDrop> allWeapons      = cm.getWeaponInventory(userId);
        List<WeaponDrop> sellableWeapons = cm.getUnlockedWeaponInventory(userId);

        if (sellableWeapons.isEmpty()) {
            long lockedCount = allWeapons.stream().filter(WeaponDrop::isLocked).count();
            String msg = lockedCount > 0
                    ? "❌ Tu n'as aucune arme vendable ! (**" + lockedCount + "** arme(s) verrouillee(s) 🔒).\n"
                      + "Utilise `/unlock id:<n>` pour deverrouiller une arme avant de la vendre."
                    : "❌ Tu n'as aucune arme a vendre!";
            event.reply(msg).setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
        long total       = sellableWeapons.stream().mapToLong(WeaponDrop::getPrice).sum();
        int lockedCount  = (int) allWeapons.stream().filter(WeaponDrop::isLocked).count();

        // Construire l'embed de confirmation en mentionnant les locked preservees
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(InventoryUI.COLOR_DANGER)
                .setTitle("⚠️ Confirmer la vente")
                .setDescription(
                    "Tu vas vendre **" + sellableWeapons.size() + "** arme(s) pour un total de **" + total + " 🪙**.\n\n" +
                    (lockedCount > 0
                        ? "🔒 **" + lockedCount + "** arme(s) verrouillee(s) seront **preservees**.\n\n"
                        : "") +
                    "⛔ Cette action est **irreversible** !");

        event.getHook()
                .editOriginalEmbeds(eb.build())
                .setComponents(InventoryUI.buildSellConfirmButtons(userId))
                .queue();
    }

    /**
     * Confirme la vente : vend UNIQUEMENT les armes non verrouillee.
     * Les armes locked restent dans l'inventaire.
     */
    private void confirmSellAll(ButtonInteractionEvent event, long userId) {
        event.deferEdit().queue();

        CaseManager cm = CaseManager.getInstance();
        CasinoManager casino = CasinoManager.getInstance();

        List<WeaponDrop> sellable = cm.getUnlockedWeaponInventory(userId);
        if (sellable.isEmpty()) {
            showMainView(event, userId);
            return;
        }

        long total = 0;
        for (WeaponDrop w : sellable) {
            cm.removeWeapon(w.getId(), userId);
            total += w.getPrice();
        }
        casino.addBalance(userId, total);
        long newBalance = casino.getBalance(userId);

        // Compter les armes restantes (locked)
        int remaining = cm.getWeaponInventory(userId).size();

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(InventoryUI.COLOR_SUCCESS)
                .setTitle("💸 Vente effectuee!")
                .setDescription("**" + sellable.size() + "** arme(s) vendues pour **" + total + " 🪙** !\n" +
                                "💰 Nouveau solde : **" + newBalance + "** 🪙"
                                + (remaining > 0
                                    ? "\n🔒 **" + remaining + "** arme(s) verrouillee(s) conservees dans ton inventaire."
                                    : ""));

        String uid = String.valueOf(userId);
        event.getHook()
                .editOriginalEmbeds(eb.build())
                .setComponents(
                    net.dv8tion.jda.api.interactions.components.ActionRow.of(
                        net.dv8tion.jda.api.interactions.components.buttons.Button
                            .primary("cinv:main:" + uid, "↩️ Retour a l'inventaire")
                    )
                )
                .queue();

        log.info("[Cases] {} a vendu {} arme(s) pour {} coins, {} arme(s) locked preservees.",
                userId, sellable.size(), total, remaining);
    }

    // =========================================================================
    // Shop – achat de caisse
    // =========================================================================

    private void handleShopButton(ButtonInteractionEvent event, String id) {
        // Format: cshop:buy:<userId>:<caseName>
        String[] parts = id.split(":", 4);
        if (parts.length < 4 || !"buy".equals(parts[1])) return;

        long buyerUserId;
        try {
            buyerUserId = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return;
        }
        String caseName = parts[3];

        // Securite
        if (event.getUser().getIdLong() != buyerUserId) {
            event.reply("❌ Ce n'est pas ton shop!").setEphemeral(true).queue();
            return;
        }

        CaseManager cm = CaseManager.getInstance();
        CasinoManager casino = CasinoManager.getInstance();

        Case targetCase = cm.getCase(caseName);
        if (targetCase == null) {
            event.reply("❌ Caisse introuvable.").setEphemeral(true).queue();
            return;
        }

        long balance = casino.getBalance(buyerUserId);
        if (balance < targetCase.getPrice()) {
            event.reply("❌ Tu n'as pas assez de 🪙!\n💰 Solde : **" + balance + "** 🪙 | Requis : **" + targetCase.getPrice() + "** 🪙")
                    .setEphemeral(true).queue();
            return;
        }

        // Achat confirme
        event.deferEdit().queue();
        casino.removeBalance(buyerUserId, targetCase.getPrice());
        cm.addToInventory(buyerUserId, caseName, 1);
        long newBalance = casino.getBalance(buyerUserId);

        // Rafraichir le shop avec note de confirmation
        String note = "✅ **" + caseName + "** achetee pour **" + targetCase.getPrice() + "** 🪙 !\n" +
                      "Utilise `/inventory` pour l'ouvrir.";
        Collection<Case> allCases = cm.getAllCases();

        event.getHook()
                .editOriginalEmbeds(ShopUI.buildShopEmbed(allCases, newBalance, note))
                .setComponents(ShopUI.buildShopButtons(buyerUserId, allCases, newBalance))
                .queue();

        log.info("[Shop] {} a achete '{}' pour {} coins.", buyerUserId, caseName, targetCase.getPrice());
    }
}
