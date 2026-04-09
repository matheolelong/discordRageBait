package com.ragebait.cases;

import java.util.Collections;
import java.util.List;

/**
 * Représente une caisse (case) qui peut être achetée et ouverte par un joueur.
 * Chaque caisse a un nom unique, un prix d'achat et une liste d'armes possibles.
 */
public class Case {

    /** Identifiant unique de la caisse, utilisé comme clé (ex: "alpha_case") */
    private final String name;

    /** Prix en coins pour acheter la caisse */
    private final long price;

    /** Liste des armes que la caisse peut contenir */
    private final List<Weapon> weapons;

    public Case(String name, long price, List<Weapon> weapons) {
        this.name    = name;
        this.price   = price;
        this.weapons = Collections.unmodifiableList(weapons);
    }

    public String       getName()    { return name;    }
    public long         getPrice()   { return price;   }
    public List<Weapon> getWeapons() { return weapons; }

    @Override
    public String toString() {
        return "Case{name='" + name + "', price=" + price + ", weapons=" + weapons.size() + "}";
    }
}
