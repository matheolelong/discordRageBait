package com.ragebait.cases;

/**
 * Représente une arme pouvant être obtenue en ouvrant une caisse.
 * Contient toutes les données nécessaires pour le système de drop :
 * probabilité, fourchette de prix et fourchette de float.
 */
public class Weapon {

    /** Nom affiché de l'arme (ex: "AK-47 Redline") */
    private final String name;

    /** Prix minimum que l'arme peut avoir (float = floatMax) */
    private final long minPrice;

    /** Prix maximum que l'arme peut avoir (float = floatMin) */
    private final long maxPrice;

    /** Probabilité de drop (entre 0.0 et 1.0, la somme des armes d'une caisse doit = 1.0) */
    private final double dropChance;

    /** Valeur de float minimum possible pour cette arme */
    private final double floatMin;

    /** Valeur de float maximum possible pour cette arme */
    private final double floatMax;

    public Weapon(String name, long minPrice, long maxPrice, double dropChance, double floatMin, double floatMax) {
        this.name = name;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.dropChance = dropChance;
        this.floatMin = floatMin;
        this.floatMax = floatMax;
    }

    public String getName()       { return name;       }
    public long   getMinPrice()   { return minPrice;   }
    public long   getMaxPrice()   { return maxPrice;   }
    public double getDropChance() { return dropChance; }
    public double getFloatMin()   { return floatMin;   }
    public double getFloatMax()   { return floatMax;   }

    @Override
    public String toString() {
        return name + " (dropChance=" + dropChance + ", float=[" + floatMin + "," + floatMax + "])";
    }
}
