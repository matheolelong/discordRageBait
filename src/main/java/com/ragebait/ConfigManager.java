package com.ragebait;

import java.io.*;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

public class ConfigManager {
    
    private static final String CONFIG_FILE = "config.txt";
    
    public static void saveConfig(GhostPingManager gp) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            // Sauvegarder la cible
            if (gp.getTargetUserId() != null) {
                writer.println("target=" + gp.getTargetUserId());
            }
            
            // Sauvegarder l'intervalle
            writer.println("interval=" + gp.getInterval());
            
            // Sauvegarder les salons
            for (Long channelId : gp.getChannelIds()) {
                writer.println("channel=" + channelId);
            }
            
            System.out.println("Configuration sauvegardée!");
        } catch (IOException e) {
            System.err.println("Erreur sauvegarde config: " + e.getMessage());
        }
    }
    
    public static void loadConfig(GhostPingManager gp) {
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
                }
            }
            System.out.println("Configuration chargée! " + gp.getChannelIds().size() + " salon(s)");
        } catch (IOException | NumberFormatException e) {
            System.err.println("Erreur chargement config: " + e.getMessage());
        }
    }
}
