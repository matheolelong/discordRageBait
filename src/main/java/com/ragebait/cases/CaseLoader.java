package com.ragebait.cases;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Charge et parse le fichier {@code cases/cases.json} au démarrage du bot.
 *
 * <p>Format attendu :</p>
 * <pre>
 * {
 *   "cases": [
 *     {
 *       "name": "alpha_case",
 *       "price": 1000,
 *       "weapons": [
 *         {
 *           "name": "AK-47 Redline",
 *           "minPrice": 500,
 *           "maxPrice": 1500,
 *           "dropChance": 0.25,
 *           "floatMin": 0.10,
 *           "floatMax": 0.40
 *         }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p>Le parsing est fait manuellement (sans bibliothèque externe) pour rester
 * cohérent avec le projet qui n'a pas de dépendance JSON.</p>
 */
public class CaseLoader {

    private static final Logger log = LoggerFactory.getLogger(CaseLoader.class);

    /** Chemin du dossier cases relatif au répertoire d'exécution */
    private static final String CASES_DIR  = "cases";

    /** Chemin du fichier de configuration des caisses */
    private static final String CASES_FILE = CASES_DIR + "/cases.json";

    /**
     * Crée le dossier {@code cases/} s'il n'existe pas, puis charge les caisses
     * depuis {@code cases/cases.json}.
     *
     * <p>Si le fichier n'existe pas encore, un fichier d'exemple est créé
     * automatiquement pour guider l'utilisateur.</p>
     *
     * @return liste des caisses chargées (jamais null, éventuellement vide)
     */
    public List<Case> loadCases() {
        // 1. Créer le dossier si besoin
        Path dir = Paths.get(CASES_DIR);
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                log.info("[CaseLoader] Dossier '{}' créé.", CASES_DIR);
            } catch (IOException e) {
                log.error("[CaseLoader] Impossible de créer le dossier '{}'.", CASES_DIR, e);
                return Collections.emptyList();
            }
        }

        // 2. Créer un fichier exemple si le fichier n'existe pas
        Path file = Paths.get(CASES_FILE);
        if (!Files.exists(file)) {
            createExampleFile(file);
            log.warn("[CaseLoader] Fichier '{}' créé avec un exemple. Modifiez-le puis redémarrez.", CASES_FILE);
            return Collections.emptyList();
        }

        // 3. Lire et parser le fichier
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<Case> cases = parseJson(json);
            log.info("[CaseLoader] {} caisse(s) chargée(s) depuis '{}'.", cases.size(), CASES_FILE);
            return cases;
        } catch (IOException e) {
            log.error("[CaseLoader] Erreur de lecture du fichier '{}'.", CASES_FILE, e);
            return Collections.emptyList();
        }
    }

    // =========================================================================
    // Parser JSON minimaliste (sans dépendance externe)
    // =========================================================================

    /**
     * Parse le JSON des caisses de manière légère, sans librairie externe.
     * Gère les cas d'erreur courants avec des messages clairs dans les logs.
     */
    private List<Case> parseJson(String json) {
        List<Case> result = new ArrayList<>();
        try {
            // Extraire le tableau "cases": [ ... ]
            String casesArray = extractArrayContent(json, "\"cases\"");
            if (casesArray == null) {
                log.error("[CaseLoader] Clé 'cases' introuvable dans le JSON.");
                return result;
            }

            // Découper en objets individuels
            List<String> caseObjects = splitJsonObjects(casesArray);
            for (String caseObj : caseObjects) {
                try {
                    Case c = parseCase(caseObj);
                    if (c != null) {
                        result.add(c);
                        log.debug("[CaseLoader] Caisse chargée : {}", c);
                    }
                } catch (Exception e) {
                    log.error("[CaseLoader] Erreur parsing d'une caisse : {}", caseObj, e);
                }
            }
        } catch (Exception e) {
            log.error("[CaseLoader] Erreur générale lors du parsing JSON.", e);
        }
        return result;
    }

    /** Parse un objet JSON représentant une caisse. */
    private Case parseCase(String json) {
        String name    = extractString(json, "\"name\"");
        long   price   = extractLong(json, "\"price\"");

        String weaponsArray = extractArrayContent(json, "\"weapons\"");
        if (name == null || weaponsArray == null) {
            log.warn("[CaseLoader] Caisse ignorée (name ou weapons manquant) : {}", json);
            return null;
        }

        List<Weapon> weapons = new ArrayList<>();
        for (String weaponObj : splitJsonObjects(weaponsArray)) {
            try {
                Weapon w = parseWeapon(weaponObj);
                if (w != null) weapons.add(w);
            } catch (Exception e) {
                log.error("[CaseLoader] Erreur parsing d'une arme : {}", weaponObj, e);
            }
        }

        if (weapons.isEmpty()) {
            log.warn("[CaseLoader] Caisse '{}' ignorée : aucune arme valide.", name);
            return null;
        }

        return new Case(name, price, weapons);
    }

    /** Parse un objet JSON représentant une arme. */
    private Weapon parseWeapon(String json) {
        String name       = extractString(json, "\"name\"");
        long   minPrice   = extractLong(json, "\"minPrice\"");
        long   maxPrice   = extractLong(json, "\"maxPrice\"");
        double dropChance = extractDouble(json, "\"dropChance\"");
        double floatMin   = extractDouble(json, "\"floatMin\"");
        double floatMax   = extractDouble(json, "\"floatMax\"");

        if (name == null) {
            log.warn("[CaseLoader] Arme ignorée (name manquant) : {}", json);
            return null;
        }

        return new Weapon(name, minPrice, maxPrice, dropChance, floatMin, floatMax);
    }

    // =========================================================================
    // Utilitaires de parsing JSON minimaliste
    // =========================================================================

    /**
     * Extrait la valeur d'un champ string : "key": "valeur"
     * Retourne null si non trouvé.
     */
    private String extractString(String json, String key) {
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) return null;
        int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx == -1) return null;
        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote == -1) return null;
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote == -1) return null;
        return json.substring(startQuote + 1, endQuote);
    }

    /**
     * Extrait la valeur d'un champ numérique entier : "key": 123
     * Retourne 0 si non trouvé.
     */
    private long extractLong(String json, String key) {
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) return 0;
        int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx == -1) return 0;
        StringBuilder sb = new StringBuilder();
        boolean started = false;
        for (int i = colonIdx + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (Character.isDigit(c)) { sb.append(c); started = true; }
            else if (started) break;
        }
        try { return Long.parseLong(sb.toString()); }
        catch (NumberFormatException e) { return 0; }
    }

    /**
     * Extrait la valeur d'un champ numérique décimal : "key": 0.25
     * Retourne 0.0 si non trouvé.
     */
    private double extractDouble(String json, String key) {
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) return 0.0;
        int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx == -1) return 0.0;
        StringBuilder sb = new StringBuilder();
        boolean started = false;
        for (int i = colonIdx + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (Character.isDigit(c) || c == '.') { sb.append(c); started = true; }
            else if (started) break;
        }
        try { return Double.parseDouble(sb.toString()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    /**
     * Extrait le contenu d'un tableau JSON associé à une clé.
     * ex: { "weapons": [ ... ] } → retourne "[ ... ]" puis le contenu entre crochets.
     */
    private String extractArrayContent(String json, String key) {
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) return null;
        int bracketOpen = json.indexOf('[', keyIdx + key.length());
        if (bracketOpen == -1) return null;
        // Trouver le crochet fermant correspondant (avec profondeur)
        int depth = 0;
        for (int i = bracketOpen; i < json.length(); i++) {
            if (json.charAt(i) == '[') depth++;
            else if (json.charAt(i) == ']') {
                depth--;
                if (depth == 0) return json.substring(bracketOpen + 1, i);
            }
        }
        return null;
    }

    /**
     * Découpe un tableau JSON (sans les crochets extérieurs) en objets individuels
     * en respectant la profondeur des accolades imbriquées.
     */
    private List<String> splitJsonObjects(String content) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    objects.add(content.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    // =========================================================================
    // Génération du fichier d'exemple
    // =========================================================================

    /** Crée un fichier {@code cases.json} d'exemple avec deux caisses de démonstration. */
    private void createExampleFile(Path file) {
        String example = """
                {
                  "cases": [
                    {
                      "name": "alpha_case",
                      "price": 1000,
                      "weapons": [
                        {
                          "name": "AK-47 Redline",
                          "minPrice": 500,
                          "maxPrice": 1500,
                          "dropChance": 0.40,
                          "floatMin": 0.10,
                          "floatMax": 0.40
                        },
                        {
                          "name": "M4A4 Howl",
                          "minPrice": 3000,
                          "maxPrice": 8000,
                          "dropChance": 0.10,
                          "floatMin": 0.00,
                          "floatMax": 0.35
                        },
                        {
                          "name": "AWP Asiimov",
                          "minPrice": 2000,
                          "maxPrice": 6000,
                          "dropChance": 0.15,
                          "floatMin": 0.18,
                          "floatMax": 0.45
                        },
                        {
                          "name": "Glock-18 Fade",
                          "minPrice": 200,
                          "maxPrice": 800,
                          "dropChance": 0.25,
                          "floatMin": 0.00,
                          "floatMax": 0.08
                        },
                        {
                          "name": "Desert Eagle Blaze",
                          "minPrice": 800,
                          "maxPrice": 2500,
                          "dropChance": 0.10,
                          "floatMin": 0.00,
                          "floatMax": 0.08
                        }
                      ]
                    },
                    {
                      "name": "gamma_case",
                      "price": 2000,
                      "weapons": [
                        {
                          "name": "M4A1-S Hyper Beast",
                          "minPrice": 1000,
                          "maxPrice": 4000,
                          "dropChance": 0.35,
                          "floatMin": 0.00,
                          "floatMax": 0.50
                        },
                        {
                          "name": "Knife Doppler",
                          "minPrice": 15000,
                          "maxPrice": 40000,
                          "dropChance": 0.05,
                          "floatMin": 0.00,
                          "floatMax": 0.08
                        },
                        {
                          "name": "AWP Dragonlore",
                          "minPrice": 10000,
                          "maxPrice": 30000,
                          "dropChance": 0.02,
                          "floatMin": 0.00,
                          "floatMax": 0.07
                        },
                        {
                          "name": "P250 Undertow",
                          "minPrice": 100,
                          "maxPrice": 500,
                          "dropChance": 0.58,
                          "floatMin": 0.06,
                          "floatMax": 0.80
                        }
                      ]
                    }
                  ]
                }
                """;
        try {
            Files.writeString(file, example, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[CaseLoader] Impossible de créer le fichier d'exemple '{}'.", CASES_FILE, e);
        }
    }
}
