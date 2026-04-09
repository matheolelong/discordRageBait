package com.ragebait.cases;

/**
 * Represente une arme droppee et stockee dans l'inventaire d'un joueur.
 *
 * <p>Chaque instance correspond a une ligne dans la table {@code weapon_inventory}.
 * Les armes sont identifiees par un {@code id} genere par la BDD (SERIAL).</p>
 */
public class WeaponDrop {

    /** Identifiant unique auto-genere par PostgreSQL */
    private final int id;

    /** ID Discord du proprietaire */
    private final long userId;

    /** Nom de l'arme (ex: "AK-47 Redline") */
    private final String weaponName;

    /** Nom de la caisse d'ou provient l'arme */
    private final String caseName;

    /** Qualite courte (FN, MW, FT, WW, BS) */
    private final String quality;

    /** Valeur de float de l'arme (4 decimales) */
    private final double floatValue;

    /** Prix de vente en coins */
    private final long price;

    public WeaponDrop(int id, long userId, String weaponName, String caseName,
                      String quality, double floatValue, long price) {
        this.id         = id;
        this.userId     = userId;
        this.weaponName = weaponName;
        this.caseName   = caseName;
        this.quality    = quality;
        this.floatValue = floatValue;
        this.price      = price;
    }

    public int    getId()         { return id;         }
    public long   getUserId()     { return userId;     }
    public String getWeaponName() { return weaponName; }
    public String getCaseName()   { return caseName;   }
    public String getQuality()    { return quality;    }
    public double getFloatValue() { return floatValue; }
    public long   getPrice()      { return price;      }
}
