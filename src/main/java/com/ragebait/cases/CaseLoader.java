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
 * <p>Le parsing est fait manuellement (sans bibliotheque externe) pour rester
 * coherent avec le projet qui n'a pas de dependance JSON.</p>
 */
public class CaseLoader {

    private static final Logger log = LoggerFactory.getLogger(CaseLoader.class);

    /** Chemin du dossier cases relatif au repertoire d'execution */
    private static final String CASES_DIR  = "cases";

    /** Chemin du fichier de configuration des caisses */
    private static final String CASES_FILE = CASES_DIR + "/cases.json";

    /**
     * Cree le dossier {@code cases/} et le fichier {@code cases.json} s'ils n'existent pas,
     * puis charge les caisses.
     *
     * <p>Si le fichier n'existait pas, un fichier avec des caisses de demonstration est
     * cree automatiquement et charge immediatement - le bot est pret sans intervention.</p>
     *
     * @return liste des caisses chargees (jamais null, eventuellement vide)
     */
    public List<Case> loadCases() {
        // 1. Creer le dossier si besoin
        Path dir = Paths.get(CASES_DIR);
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                log.info("[CaseLoader] Dossier '{}' cree.", CASES_DIR);
            } catch (IOException e) {
                log.error("[CaseLoader] Impossible de creer le dossier '{}'.", CASES_DIR, e);
                return Collections.emptyList();
            }
        }

        // 2. Creer un fichier avec les caisses de demo si absent, puis charger quand meme
        Path file = Paths.get(CASES_FILE);
        if (!Files.exists(file)) {
            createExampleFile(file);
            log.info("[CaseLoader] Fichier '{}' cree avec les caisses de demonstration.", CASES_FILE);
        }

        // 3. Lire et parser le fichier
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<Case> cases = parseJson(json);
            log.info("[CaseLoader] {} caisse(s) chargee(s) depuis '{}'.", cases.size(), CASES_FILE);
            return cases;
        } catch (IOException e) {
            log.error("[CaseLoader] Erreur de lecture du fichier '{}'.", CASES_FILE, e);
            return Collections.emptyList();
        }
    }

    // =========================================================================
    // Parser JSON minimaliste (sans dependance externe)
    // =========================================================================

    /**
     * Parse le JSON des caisses de maniere legere, sans librairie externe.
     * Gere les cas d'erreur courants avec des messages clairs dans les logs.
     */
    private List<Case> parseJson(String json) {
        List<Case> result = new ArrayList<>();
        try {
            // Extraire le tableau "cases": [ ... ]
            String casesArray = extractArrayContent(json, "\"cases\"");
            if (casesArray == null) {
                log.error("[CaseLoader] Cle 'cases' introuvable dans le JSON.");
                return result;
            }

            // Decouper en objets individuels
            List<String> caseObjects = splitJsonObjects(casesArray);
            for (String caseObj : caseObjects) {
                try {
                    Case c = parseCase(caseObj);
                    if (c != null) {
                        result.add(c);
                        log.debug("[CaseLoader] Caisse chargee : {}", c);
                    }
                } catch (Exception e) {
                    log.error("[CaseLoader] Erreur parsing d'une caisse : {}", caseObj, e);
                }
            }
        } catch (Exception e) {
            log.error("[CaseLoader] Erreur generale lors du parsing JSON.", e);
        }
        return result;
    }

    /** Parse un objet JSON representant une caisse. */
    private Case parseCase(String json) {
        String name    = extractString(json, "\"name\"");
        long   price   = extractLong(json, "\"price\"");

        String weaponsArray = extractArrayContent(json, "\"weapons\"");
        if (name == null || weaponsArray == null) {
            log.warn("[CaseLoader] Caisse ignoree (name ou weapons manquant) : {}", json);
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
            log.warn("[CaseLoader] Caisse '{}' ignoree : aucune arme valide.", name);
            return null;
        }

        return new Case(name, price, weapons);
    }

    /** Parse un objet JSON representant une arme. */
    private Weapon parseWeapon(String json) {
        String name       = extractString(json, "\"name\"");
        long   minPrice   = extractLong(json, "\"minPrice\"");
        long   maxPrice   = extractLong(json, "\"maxPrice\"");
        double dropChance = extractDouble(json, "\"dropChance\"");
        double floatMin   = extractDouble(json, "\"floatMin\"");
        double floatMax   = extractDouble(json, "\"floatMax\"");

        if (name == null) {
            log.warn("[CaseLoader] Arme ignoree (name manquant) : {}", json);
            return null;
        }

        return new Weapon(name, minPrice, maxPrice, dropChance, floatMin, floatMax);
    }

    // =========================================================================
    // Utilitaires de parsing JSON minimaliste
    // =========================================================================

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

    private String extractArrayContent(String json, String key) {
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) return null;
        int bracketOpen = json.indexOf('[', keyIdx + key.length());
        if (bracketOpen == -1) return null;
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
    // Generation du fichier d'exemple
    // =========================================================================

    /** Cree un fichier {@code cases.json} avec des caisses de demonstration. */
    private void createExampleFile(Path file) {
        String example = "{\n"
            + "  \"cases\": [\n"
            + "    {\n"
            + "      \"name\": \"alpha_case\",\n"
            + "      \"price\": 1000,\n"
            + "      \"weapons\": [\n"
            + "        {\n"
            + "          \"name\": \"AK-47 Redline\",\n"
            + "          \"minPrice\": 500,\n"
            + "          \"maxPrice\": 1500,\n"
            + "          \"dropChance\": 0.40,\n"
            + "          \"floatMin\": 0.10,\n"
            + "          \"floatMax\": 0.40\n"
            + "        },\n"
            + "        {\n"
            + "          \"name\": \"AWP Asiimov\",\n"
            + "          \"minPrice\": 2000,\n"
            + "          \"maxPrice\": 6000,\n"
            + "          \"dropChance\": 0.15,\n"
            + "          \"floatMin\": 0.18,\n"
            + "          \"floatMax\": 0.45\n"
            + "        },\n"
            + "        {\n"
            + "          \"name\": \"M4A4 Howl\",\n"
            + "          \"minPrice\": 3000,\n"
            + "          \"maxPrice\": 8000,\n"
            + "          \"dropChance\": 0.10,\n"
            + "          \"floatMin\": 0.00,\n"
            + "          \"floatMax\": 0.35\n"
            + "        },\n"
            + "        {\n"
            + "          \"name\": \"Glock-18 Fade\",\n"
            + "          \"minPrice\": 200,\n"
            + "          \"maxPrice\": 800,\n"
            + "          \"dropChance\": 0.25,\n"
            + "          \"floatMin\": 0.00,\n"
            + "          \"floatMax\": 0.08\n"
            + "        },\n"
            + "        {\n"
            + "          \"name\": \"Desert Eagle Blaze\",\n"
            + "          \"minPrice\": 800,\n"
            + "          \"maxPrice\": 2500,\n"
            + "          \"dropChance\": 0.10,\n"
            + "          \"floatMin\": 0.00,\n"
            + "          \"floatMax\": 0.08\n"
            + "        }\n"
            + "      ]\n"
            + "    },\n"
            + "    {\n"
            + "      \"name\": \"gamma_case\",\n"
            + "      \"price\": 2000,\n"
            + "      \"weapons\": [\n"
            + "        {\n"
            + "          \"name\": \"M4A1-S Hyper Beast\",\n"
            + "          \"minPrice\": 1000,\n"
            + "          \"maxPrice\": 4000,\n"
            + "          \"dropChance\": 0.35,\n"
            + "          \"floatMin\": 0.00,\n"
            + "          \"floatMax\": 0.50\n"
            + "        },\n"
            + "        {\n"
            + "          \"name\": \"P250 Undertow\",\n"
            + "          \"minPrice\": 100,\n"
            + "          \"maxPrice\": 500,\n"
            + "          \"dropChance\": 0.58,\n"
            + "          \"floatMin\": 0.06,\n"
            + "          \"floatMax\": 0.80\n"
            + "        },\n"
            + "        {\n"
            + "          \"name\": \"AWP Dragonlore\",\n"
            + "          \"minPrice\": 10000,\n"
            + "          \"maxPrice\": 30000,\n"
            + "          \"dropChance\": 0.02,\n"
            + "          \"floatMin\": 0.00,\n"
            + "          \"floatMax\": 0.07\n"
            + "        },\n"
            + "        {\n"
            + "          \"name\": \"Knife Doppler\",\n"
            + "          \"minPrice\": 15000,\n"
            + "          \"maxPrice\": 40000,\n"
            + "          \"dropChance\": 0.05,\n"
            + "          \"floatMin\": 0.00,\n"
            + "          \"floatMax\": 0.08\n"
            + "        }\n"
            + "      ]\n"
            + "    },\n"
            + "    {\n"
            + "      \"name\": \"operation_case\",\n"
            + "      \"price\": 5000,\n"
            + "      \"weapons\": [\n"
            + "        {\n"
            + "          \"name\": \"USP-S Kill Confirmed\",\n"
            + "          \"minPrice\": 2000,\n"
            + "          \"maxPrice\": 7000,\n"
            + "          \"dropChance\": 0.20,\n"
            + "          \"floatMin\": 0.00,\n"
            + "          \"floatMax\": 0.40\n"
            + "        },\n"
            + "        {\n"
            + "          \"name\": \"AK-47 Fire Serpent\",\n"
            + "          \"minPrice\": 8000,\n"
            + "          \"maxPrice\": 20000,\n"
            + "          \"dropChance\": 0.08,\n"
            + "          \"floatMin\": 0.00,\n"
            + "          \"floatMax\": 0.38\n"
            + "        },\n"
            + "        {\n"
            + "          \"name\": \"Karambit Fade\",\n"
            + "          \"minPrice\": 25000,\n"
            + "          \"maxPrice\": 60000,\n"
            + "          \"dropChance\": 0.025,\n"
            + "          \"floatMin\": 0.00,\n"
            + "          \"floatMax\": 0.08\n"
            + "        },\n"
            + "        {\n"
            + "          \"name\": \"P90 Death Grip\",\n"
            + "          \"minPrice\": 200,\n"
            + "          \"maxPrice\": 800,\n"
            + "          \"dropChance\": 0.695,\n"
            + "          \"floatMin\": 0.06,\n"
            + "          \"floatMax\": 0.80\n"
            + "        }\n"
            + "      ]\n"
            + "    }\n"
            + "  ]\n"
            + "}\n";
        try {
            Files.writeString(file, example, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[CaseLoader] Impossible de creer le fichier d'exemple '{}'.", CASES_FILE, e);
        }
    }
}
