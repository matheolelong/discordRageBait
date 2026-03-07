package com.ragebait;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    
    private static final String CONFIG_FILE = "config.txt";
    
    public static void saveConfig(GhostPingManager gp) {
        // Charger les exclusions existantes pour ne pas les perdre
        QuiExclusionManager qe = QuiExclusionManager.getInstance();
        saveAll(gp, qe);
    }
    
    public static void saveQuiExclusions(QuiExclusionManager qe) {
        // Charger la config ghost ping existante pour ne pas la perdre
        GhostPingManager gp = GhostPingManager.getInstance();
        saveAll(gp, qe);
    }
    
    private static void saveAll(GhostPingManager gp, QuiExclusionManager qe) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            // Sauvegarder la cible ghost ping
            if (gp.getTargetUserId() != null) {
                writer.println("target=" + gp.getTargetUserId());
            }
            
            // Sauvegarder l'intervalle
            writer.println("interval=" + gp.getInterval());
            
            // Sauvegarder les salons
            for (Long channelId : gp.getChannelIds()) {
                writer.println("channel=" + channelId);
            }
            
            // Sauvegarder les exclusions "Qui t'a demandé"
            for (Long userId : qe.getExcludedUsers()) {
                writer.println("quiexclude=" + userId);
            }
            
        } catch (IOException e) {
            System.err.println("Erreur sauvegarde config: " + e.getMessage());
        }
    }
    
    public static void loadConfig(GhostPingManager gp) {
        QuiExclusionManager qe = QuiExclusionManager.getInstance();
        
        Path configPath = Paths.get(CONFIG_FILE);
        if (!Files.exists(configPath)) {
            System.out.println("Aucune configuration trouvée.");
            return;
        }
        
        try {
            for (String line : Files.readAllLines(configPath)) {
                if (line.startsWith("target=")) {
                    long targetId = Long.parseLong(line.substring(7));
                    gp.setTargetUser(targetId);
                } else if (line.startsWith("interval=")) {
                    int interval = Integer.parseInt(line.substring(9));
                    gp.setInterval(interval);
                } else if (line.startsWith("channel=")) {
                    long channelId = Long.parseLong(line.substring(8));
                    gp.addChannel(channelId);
                } else if (line.startsWith("quiexclude=")) {
                    long userId = Long.parseLong(line.substring(11));
                    qe.addExclusion(userId);
                }
            }
            System.out.println("Configuration chargée! " + gp.getChannelIds().size() + " salon(s), " + qe.getExcludedUsers().size() + " exclusion(s)");
        } catch (IOException | NumberFormatException e) {
            System.err.println("Erreur chargement config: " + e.getMessage());
        }
    }
}
