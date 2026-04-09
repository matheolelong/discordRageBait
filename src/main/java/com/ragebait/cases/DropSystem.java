package com.ragebait.cases;

import java.util.List;
import java.util.Random;

/**
 * Gère le tirage pondéré d'une arme dans une caisse (drop system).
 *
 * <p>Chaque arme possède un {@code dropChance} (entre 0.0 et 1.0).
 * Le tirage utilise une roue de la fortune pondérée :</p>
 * <ol>
 *   <li>On additionne tous les poids pour obtenir le total.</li>
 *   <li>On tire un nombre aléatoire entre 0 et ce total.</li>
 *   <li>On parcourt les armes en soustrayant leur poids jusqu'à atteindre 0.</li>
 * </ol>
 *
 * <p>Cette méthode fonctionne même si la somme des dropChance ≠ 1.0,
 * ce qui simplifie la configuration des caisses.</p>
 */
public class DropSystem {

    private final Random random;

    public DropSystem(Random random) {
        this.random = random;
    }

    /**
     * Tire une arme aléatoire dans la liste selon les probabilités pondérées.
     *
     * @param weapons liste des armes disponibles (non vide)
     * @return l'arme tirée, ou null si la liste est vide
     */
    public Weapon roll(List<Weapon> weapons) {
        if (weapons == null || weapons.isEmpty()) return null;

        // 1. Calculer la somme totale des poids
        double totalWeight = weapons.stream()
                .mapToDouble(Weapon::getDropChance)
                .sum();

        if (totalWeight <= 0) {
            // Fallback : distribution uniforme si tous les poids sont 0
            return weapons.get(random.nextInt(weapons.size()));
        }

        // 2. Tirer un nombre aléatoire dans [0, totalWeight[
        double roll = random.nextDouble() * totalWeight;

        // 3. Sélectionner l'arme correspondante
        double cumulative = 0.0;
        for (Weapon weapon : weapons) {
            cumulative += weapon.getDropChance();
            if (roll < cumulative) {
                return weapon;
            }
        }

        // Sécurité : retourner la dernière arme (erreur d'arrondi possible)
        return weapons.get(weapons.size() - 1);
    }

    /**
     * Génère une liste de {@code count} armes tirées aléatoirement pour l'animation de défilement.
     * Ces armes ne sont pas réelles : elles servent uniquement à l'effet visuel.
     *
     * @param weapons liste source des armes
     * @param count   nombre d'armes à générer pour l'animation
     * @return tableau d'armes aléatoires pour l'animation
     */
    public Weapon[] generateAnimationReel(List<Weapon> weapons, int count) {
        Weapon[] reel = new Weapon[count];
        for (int i = 0; i < count; i++) {
            // Distribution uniforme pour l'animation (pas pondérée)
            reel[i] = weapons.get(random.nextInt(weapons.size()));
        }
        return reel;
    }
}
