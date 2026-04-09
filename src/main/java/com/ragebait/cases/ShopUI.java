package com.ragebait.cases;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.Color;
import java.util.*;

/**
 * Fabrique l'embed et les boutons pour le shop de caisses (/cases).
 *
 * <p>Convention des IDs de bouton :</p>
 * <pre>
 *   cshop:buy:&lt;userId&gt;:&lt;caseName&gt;
 * </pre>
 */
public class ShopUI {

    private static final Color COLOR_SHOP = new Color(0xFEE75C); // Jaune or

    /**
     * Embed principal du shop avec la liste des caisses et le solde du joueur.
     *
     * @param cases   caisses disponibles
     * @param balance solde actuel du joueur (pour indiquer ce qui est abordable)
     * @param note    message optionnel (ex: confirmation d'achat), ou null
     */
    public static MessageEmbed buildShopEmbed(Collection<Case> cases, long balance, String note) {
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(COLOR_SHOP)
                .setTitle("🛒 Boutique de Caisses")
                .setDescription("💰 Ton solde : **" + balance + "** 🪙"
                        + (note != null ? "\n\n" + note : ""));

        for (Case c : cases) {
            boolean canAfford = balance >= c.getPrice();
            String status = canAfford ? "✅" : "❌";
            eb.addField(
                    status + " 🎁 " + c.getName(),
                    "💳 **" + c.getPrice() + "** 🪙 | " + c.getWeapons().size() + " skin(s) possible(s)",
                    false
            );
        }

        eb.setFooter("Clique sur un bouton pour acheter une caisse");
        return eb.build();
    }

    /**
     * Boutons du shop : un bouton Buy par caisse, desactive si pas assez de coins.
     * Max 3 boutons par ligne pour la lisibilite.
     */
    public static List<ActionRow> buildShopButtons(long userId, Collection<Case> cases, long balance) {
        String uid = String.valueOf(userId);
        List<Button> buttons = new ArrayList<>();

        for (Case c : cases) {
            if (buttons.size() >= 15) break; // max 3 lignes * 5 = 15
            boolean canAfford = balance >= c.getPrice();
            Button btn = Button.success(
                    "cshop:buy:" + uid + ":" + c.getName(),
                    "🛒 " + c.getName() + " (" + c.getPrice() + " 🪙)"
            );
            buttons.add(canAfford ? btn : btn.asDisabled());
        }

        // Grouper par lignes de 3
        List<ActionRow> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += 3) {
            rows.add(ActionRow.of(buttons.subList(i, Math.min(i + 3, buttons.size()))));
        }
        return rows;
    }
}
