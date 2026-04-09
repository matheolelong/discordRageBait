package com.ragebait.cases;

import java.util.Random;

/**
 * Calcule le float d'une arme et en déduit sa qualité (condition) et son prix final.
 *
 * <p>Le float est un nombre entre {@code floatMin} et {@code floatMax} de l'arme.
 * Plus le float est proche de {@code floatMin}, plus le prix est proche de {@code maxPrice},
 * et inversement.</p>
 *
 * <p>Correspondance qualité ↔ float (comme CS:GO) :</p>
 * <ul>
 *   <li>0.00–0.07 → Factory New (FN)</li>
 *   <li>0.07–0.15 → Minimal Wear (MW)</li>
 *   <li>0.15–0.38 → Field-Tested (FT)</li>
 *   <li>0.38–0.45 → Well-Worn (WW)</li>
 *   <li>0.45–1.00 → Battle-Scarred (BS)</li>
 * </ul>
 */
public class FloatCalculator {

    private final Random random;

    public FloatCalculator(Random random) {
        this.random = random;
    }

    // =========================================================================
    // Génération du float
    // =========================================================================

    /**
     * Génère un float aléatoire dans la plage {@code [floatMin, floatMax]} de l'arme.
     *
     * @param weapon l'arme pour laquelle générer le float
     * @return float généré (4 décimales)
     */
    public double generateFloat(Weapon weapon) {
        double range  = weapon.getFloatMax() - weapon.getFloatMin();
        double value  = weapon.getFloatMin() + (random.nextDouble() * range);
        // Arrondir à 4 décimales pour l'affichage
        return Math.round(value * 10000.0) / 10000.0;
    }

    // =========================================================================
    // Qualité
    // =========================================================================

    /**
     * Retourne la qualité (condition) correspondant à la valeur de float absolue.
     * Basé sur les seuils officiels de CS:GO.
     *
     * @param floatValue valeur de float brute (entre 0.0 et 1.0)
     * @return libellé de qualité avec emoji
     */
    public String getQuality(double floatValue) {
        if (floatValue < 0.07)  return "✨ Factory New";
        if (floatValue < 0.15)  return "🟢 Minimal Wear";
        if (floatValue < 0.38)  return "🔵 Field-Tested";
        if (floatValue < 0.45)  return "🟡 Well-Worn";
        return                         "🔴 Battle-Scarred";
    }

    /**
     * Retourne le code court de la qualité (FN, MW, FT, WW, BS).
     */
    public String getQualityShort(double floatValue) {
        if (floatValue < 0.07)  return "FN";
        if (floatValue < 0.15)  return "MW";
        if (floatValue < 0.38)  return "FT";
        if (floatValue < 0.45)  return "WW";
        return                         "BS";
    }

    // =========================================================================
    // Calcul du prix final
    // =========================================================================

    /**
     * Calcule le prix final d'une arme en fonction de son float.
     *
     * <p>Formule : le float est normalisé entre 0 (floatMin) et 1 (floatMax),
     * puis interpolé linéairement entre maxPrice et minPrice.</p>
     *
     * @param weapon     l'arme dont on veut calculer le prix
     * @param floatValue le float généré pour cette arme
     * @return prix final en coins (arrondi)
     */
    public long calculatePrice(Weapon weapon, double floatValue) {
        double range = weapon.getFloatMax() - weapon.getFloatMin();
        if (range <= 0) {
            // Plage invalide → retourner la moyenne
            return (weapon.getMinPrice() + weapon.getMaxPrice()) / 2;
        }

        // t = 0 → floatMin (prix max) / t = 1 → floatMax (prix min)
        double t = (floatValue - weapon.getFloatMin()) / range;
        t = Math.max(0.0, Math.min(1.0, t)); // clamp

        long price = Math.round(weapon.getMaxPrice() - t * (weapon.getMaxPrice() - weapon.getMinPrice()));
        return Math.max(weapon.getMinPrice(), Math.min(weapon.getMaxPrice(), price));
    }
}
