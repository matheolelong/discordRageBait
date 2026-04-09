package com.ragebait.cases;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.Color;
import java.util.*;

/**
 * Fabrique les embeds et composants (boutons) pour l'inventaire joueur.
 *
 * <p>Vues disponibles :</p>
 * <ul>
 *   <li>Main  – resume : coins, caisses, armes, valeur</li>
 *   <li>Cases – liste des caisses possedees + boutons Ouvrir</li>
 *   <li>Weapons – liste des armes + bouton Vendre Tout</li>
 *   <li>SellConfirm – confirmation avant vente globale</li>
 *   <li>ReadOnly – vue non interactive pour /inventory @autre</li>
 * </ul>
 *
 * <p>Convention des IDs de bouton :</p>
 * <pre>
 *   cinv:main:&lt;userId&gt;
 *   cinv:cases:&lt;userId&gt;
 *   cinv:weapons:&lt;userId&gt;
 *   cinv:open:&lt;userId&gt;:&lt;caseName&gt;
 *   cinv:sell:&lt;userId&gt;
 *   cinv:sellc:&lt;userId&gt;
 *   cinv:selln:&lt;userId&gt;
 * </pre>
 */
public class InventoryUI {

    // Palette de couleurs
    public static final Color COLOR_MAIN    = new Color(0x5865F2); // Blurple Discord
    public static final Color COLOR_CASES   = new Color(0xF0A500); // Or
    public static final Color COLOR_WEAPONS = new Color(0x57F287); // Vert
    public static final Color COLOR_DANGER  = new Color(0xED4245); // Rouge
    public static final Color COLOR_SUCCESS = new Color(0x2ECC71); // Vert clair

    // =========================================================================
    // VUE PRINCIPALE (self)
    // =========================================================================

    /**
     * Embed principal resument l'inventaire du joueur.
     */
    public static MessageEmbed buildMainEmbed(String userName, String avatarUrl,
            Map<String, Integer> cases, List<WeaponDrop> weapons, long balance) {

        int totalCases = cases.values().stream().mapToInt(Integer::intValue).sum();
        long totalValue = weapons.stream().mapToLong(WeaponDrop::getPrice).sum();

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(COLOR_MAIN)
                .setTitle("🎒 Inventaire de " + userName)
                .setThumbnail(avatarUrl)
                .addField("💰 Solde", "**" + balance + "** 🪙", true)
                .addField("📦 Caisses", "**" + totalCases + "** caisse(s)", true)
                .addField("🔫 Armes", "**" + weapons.size() + "** arme(s)", true)
                .addField("💎 Valeur armes", "**" + totalValue + "** 🪙", true);

        // Apercu des caisses (max 5)
        if (!cases.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (Map.Entry<String, Integer> e : cases.entrySet()) {
                if (shown++ >= 5) { sb.append("*... → Onglet Caisses*"); break; }
                sb.append("🎁 **").append(e.getKey()).append("** x").append(e.getValue()).append("\n");
            }
            eb.addField("📦 Caisses possedees", sb.toString().trim(), false);
        }

        // Apercu des armes (max 5)
        if (!weapons.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (WeaponDrop w : weapons) {
                if (shown++ >= 5) { sb.append("*... → Onglet Armes*"); break; }
                sb.append("`#").append(w.getId()).append("` **").append(w.getWeaponName()).append("**")
                        .append(" | ").append(qualityEmoji(w.getQuality())).append(" ").append(w.getQuality())
                        .append(" | **").append(w.getPrice()).append("** 🪙\n");
            }
            eb.addField("🔫 Dernieres armes", sb.toString().trim(), false);
        }

        eb.setFooter("Navigue avec les boutons ci-dessous");
        return eb.build();
    }

    /**
     * Boutons de la vue principale (self).
     * Ligne 1 : navigation + Vendre Tout
     * Ligne 2 : boutons Ouvrir par caisse (si possedees)
     */
    public static List<ActionRow> buildMainButtons(long userId,
            Map<String, Integer> cases, List<WeaponDrop> weapons) {

        String uid = String.valueOf(userId);
        List<ActionRow> rows = new ArrayList<>();

        // Ligne 1 : navigation
        int caseCount = cases.values().stream().mapToInt(Integer::intValue).sum();
        List<Button> nav = new ArrayList<>();
        nav.add(Button.primary("cinv:cases:" + uid, "📦 Caisses (" + caseCount + ")"));
        nav.add(Button.primary("cinv:weapons:" + uid, "🔫 Armes (" + weapons.size() + ")"));
        if (!weapons.isEmpty()) {
            nav.add(Button.danger("cinv:sell:" + uid, "💸 Vendre Tout"));
        }
        rows.add(ActionRow.of(nav));

        // Ligne 2 : boutons Ouvrir (max 5)
        if (!cases.isEmpty()) {
            List<Button> openBtns = new ArrayList<>();
            for (Map.Entry<String, Integer> e : cases.entrySet()) {
                if (openBtns.size() >= 5) break;
                String label = "🎁 " + e.getKey() + " (x" + e.getValue() + ")";
                if (label.length() > 80) label = "🎁 " + e.getKey().substring(0, 10) + "... (x" + e.getValue() + ")";
                openBtns.add(Button.success("cinv:open:" + uid + ":" + e.getKey(), label));
            }
            if (!openBtns.isEmpty()) rows.add(ActionRow.of(openBtns));
        }

        return rows;
    }

    // =========================================================================
    // ONGLET CAISSES
    // =========================================================================

    public static MessageEmbed buildCasesEmbed(String userName, Map<String, Integer> cases) {
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(COLOR_CASES)
                .setTitle("📦 Caisses de " + userName);

        if (cases.isEmpty()) {
            eb.setDescription("Tu n'as aucune caisse.\nUtilise `/cases` pour en acheter!");
        } else {
            cases.forEach((name, qty) ->
                eb.addField("🎁 " + name, "Quantite : **" + qty + "**", true)
            );
        }
        eb.setFooter("Clique sur un bouton pour ouvrir une caisse");
        return eb.build();
    }

    public static List<ActionRow> buildCasesButtons(long userId, Map<String, Integer> cases) {
        String uid = String.valueOf(userId);
        List<ActionRow> rows = new ArrayList<>();

        // Boutons Ouvrir
        if (!cases.isEmpty()) {
            List<Button> openBtns = new ArrayList<>();
            for (Map.Entry<String, Integer> e : cases.entrySet()) {
                if (openBtns.size() >= 5) break;
                openBtns.add(Button.success("cinv:open:" + uid + ":" + e.getKey(),
                        "🎁 Ouvrir " + e.getKey() + " (x" + e.getValue() + ")"));
            }
            rows.add(ActionRow.of(openBtns));
        }

        // Bouton retour
        rows.add(ActionRow.of(Button.secondary("cinv:main:" + uid, "⬅️ Retour")));
        return rows;
    }

    // =========================================================================
    // ONGLET ARMES
    // =========================================================================

    public static MessageEmbed buildWeaponsEmbed(String userName, List<WeaponDrop> weapons) {
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(COLOR_WEAPONS)
                .setTitle("🔫 Armes de " + userName);

        if (weapons.isEmpty()) {
            eb.setDescription("Tu n'as aucune arme.\nOuvre des caisses avec les boutons !");
        } else {
            long total = weapons.stream().mapToLong(WeaponDrop::getPrice).sum();
            eb.setDescription("**" + weapons.size() + "** arme(s) — Valeur totale : **" + total + " 🪙**\n");

            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (WeaponDrop w : weapons) {
                if (shown++ >= 15) {
                    sb.append("*...et ").append(weapons.size() - 15).append(" autre(s). Utilise `/weapons`*\n");
                    break;
                }
                sb.append("`#").append(w.getId()).append("` **").append(w.getWeaponName()).append("**")
                        .append(" | ").append(qualityEmoji(w.getQuality())).append(" ").append(w.getQuality())
                        .append(" | `").append(String.format("%.4f", w.getFloatValue())).append("`")
                        .append(" | **").append(w.getPrice()).append("** 🪙")
                        .append(" → `/sell id:").append(w.getId()).append("`\n");
            }
            if (sb.length() > 0) eb.addField("", sb.toString().trim(), false);
        }
        eb.setFooter("Utilise /sell id:<n> pour vendre une arme individuelle");
        return eb.build();
    }

    public static List<ActionRow> buildWeaponsButtons(long userId, boolean hasWeapons) {
        String uid = String.valueOf(userId);
        List<Button> btns = new ArrayList<>();
        btns.add(Button.secondary("cinv:main:" + uid, "⬅️ Retour"));
        if (hasWeapons) {
            btns.add(Button.danger("cinv:sell:" + uid, "💸 Vendre Tout"));
        }
        return List.of(ActionRow.of(btns));
    }

    // =========================================================================
    // CONFIRMATION VENTE TOTALE
    // =========================================================================

    public static MessageEmbed buildSellConfirmEmbed(int weaponCount, long totalValue) {
        return new EmbedBuilder()
                .setColor(COLOR_DANGER)
                .setTitle("⚠️ Confirmer la vente")
                .setDescription(
                    "Tu vas vendre **" + weaponCount + " arme(s)** pour un total de **" + totalValue + " 🪙**.\n\n" +
                    "⛔ Cette action est **irreversible** !")
                .build();
    }

    public static List<ActionRow> buildSellConfirmButtons(long userId) {
        String uid = String.valueOf(userId);
        return List.of(ActionRow.of(
                Button.danger("cinv:sellc:" + uid, "✅ Confirmer la vente"),
                Button.secondary("cinv:selln:" + uid, "❌ Annuler")
        ));
    }

    // =========================================================================
    // VUE LECTURE SEULE (autre joueur)
    // =========================================================================

    /**
     * Embed complet sans boutons pour la vue d'un autre joueur.
     */
    public static MessageEmbed buildReadOnlyEmbed(String userName, String avatarUrl,
            Map<String, Integer> cases, List<WeaponDrop> weapons, long balance) {

        int totalCases = cases.values().stream().mapToInt(Integer::intValue).sum();
        long totalValue = weapons.stream().mapToLong(WeaponDrop::getPrice).sum();

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(COLOR_MAIN)
                .setTitle("🎒 Inventaire de " + userName)
                .setThumbnail(avatarUrl)
                .addField("💰 Solde", "**" + balance + "** 🪙", true)
                .addField("📦 Caisses", "**" + totalCases + "** caisse(s)", true)
                .addField("🔫 Armes", "**" + weapons.size() + "** arme(s)", true)
                .addField("💎 Valeur armes", "**" + totalValue + "** 🪙", true);

        if (!cases.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            cases.forEach((n, q) -> sb.append("🎁 **").append(n).append("** x").append(q).append("\n"));
            eb.addField("📦 Caisses", sb.toString().trim(), false);
        }

        if (!weapons.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (WeaponDrop w : weapons) {
                if (shown++ >= 10) { sb.append("*... et ").append(weapons.size() - 10).append(" autre(s)*"); break; }
                sb.append("`#").append(w.getId()).append("` **").append(w.getWeaponName()).append("**")
                        .append(" | ").append(qualityEmoji(w.getQuality())).append(" ").append(w.getQuality())
                        .append(" | **").append(w.getPrice()).append("** 🪙\n");
            }
            eb.addField("🔫 Armes", sb.toString().trim(), false);
        }

        eb.setFooter("Lecture seule");
        return eb.build();
    }

    // =========================================================================
    // UTILITAIRES
    // =========================================================================

    /** Retourne l'emoji correspondant au code de qualite. */
    public static String qualityEmoji(String quality) {
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
