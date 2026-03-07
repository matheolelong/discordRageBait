package com.ragebait;

import com.ragebait.listeners.MessageListener;
import com.ragebait.listeners.PresenceListener;
import com.ragebait.listeners.SlashCommandListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import java.io.*;
import java.nio.file.*;

public class Main {

    public static void main(String[] args) {
        // Récupère le token depuis les variables d'environnement ou les arguments
        String token = System.getenv("DISCORD_TOKEN");
        
        if (token == null && args.length > 0) {
            token = args[0];
        }
        
        // Essayer de lire le token depuis config.txt
        if (token == null) {
            try {
                for (String line : Files.readAllLines(Path.of("config.txt"))) {
                    if (line.startsWith("token=")) {
                        token = line.substring(6).trim();
                        break;
                    }
                }
            } catch (IOException ignored) {}
        }
        
        if (token == null) {
            System.err.println("Erreur: Token Discord non trouvé!");
            System.err.println("Définissez la variable d'environnement DISCORD_TOKEN, passez le token en argument,");
            System.err.println("ou ajoutez 'token=VOTRE_TOKEN' dans config.txt");
            System.exit(1);
        }

        try {
            // Création du bot avec JDA
            JDA jda = JDABuilder.createDefault(token)
                    // Intents nécessaires pour lire les messages, le vocal et les présences
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES, 
                                   GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_PRESENCES,
                                   GatewayIntent.GUILD_MEMBERS)
                    // Cache tous les membres pour recevoir les événements de présence
                    .setMemberCachePolicy(net.dv8tion.jda.api.utils.MemberCachePolicy.ALL)
                    .enableCache(net.dv8tion.jda.api.utils.cache.CacheFlag.ONLINE_STATUS, 
                                 net.dv8tion.jda.api.utils.cache.CacheFlag.ACTIVITY)
                    // Statut affiché sur Discord
                    .setActivity(Activity.playing("Rage Bait 🎣"))
                    // Ajout des listeners
                    .addEventListeners(new MessageListener())
                    .addEventListeners(new SlashCommandListener())
                    .addEventListeners(new PresenceListener())
                    .build();

            // Attendre que le bot soit prêt
            jda.awaitReady();

            // Initialiser le GhostPingManager
            GhostPingManager ghostPing = GhostPingManager.getInstance();
            ghostPing.setJda(jda);
            
            // Initialiser le RandomMuteManager
            RandomMuteManager randomMute = RandomMuteManager.getInstance();
            randomMute.setJda(jda);
            
            // Initialiser le StatusTrackerManager
            StatusTrackerManager statusTracker = StatusTrackerManager.getInstance();
            statusTracker.setJda(jda);
            
            // Initialiser le CasinoManager
            CasinoManager casino = CasinoManager.getInstance();
            casino.loadData();
            
            // Charger la configuration sauvegardée
            ConfigManager.loadConfig(ghostPing);

            // Enregistrer les commandes slash pour chaque serveur (mise à jour instantanée)
            for (var guild : jda.getGuilds()) {
                guild.updateCommands().addCommands(
                        Commands.slash("ping", "Répond avec Pong!"),
                        Commands.slash("ragebait", "Envoie un message provocateur")
                                .addOption(OptionType.STRING, "message", "Le message à envoyer", false),
                        Commands.slash("info", "Affiche les informations du bot"),
                        // Commandes Ghost Ping
                        Commands.slash("ghostping", "Gère le ghost ping automatique")
                                .addOption(OptionType.STRING, "action", "start/stop/status", true),
                        Commands.slash("settarget", "Définit la cible du ghost ping")
                                .addOption(OptionType.USER, "user", "L'utilisateur à ping", true),
                        Commands.slash("addchannel", "Ajoute un salon pour le ghost ping")
                                .addOption(OptionType.CHANNEL, "channel", "Le salon à ajouter", true),
                        Commands.slash("removechannel", "Retire un salon du ghost ping")
                                .addOption(OptionType.CHANNEL, "channel", "Le salon à retirer", true),
                        Commands.slash("setinterval", "Change l'intervalle entre les ghost pings")
                                .addOption(OptionType.INTEGER, "minutes", "Intervalle en minutes", true)
                                .addOption(OptionType.INTEGER, "secondes", "Secondes supplémentaires", false),
                        // Commande Random Mute
                        Commands.slash("randommute", "Mute/deafen aléatoire sur une cible")
                                .addOption(OptionType.STRING, "action", "start/stop/status", true)
                                .addOption(OptionType.USER, "user", "L'utilisateur à mute (pour start)", false)
                                .addOption(OptionType.INTEGER, "delai", "Délai max en secondes (défaut: 30)", false)
                                .addOption(OptionType.INTEGER, "duree", "Durée du mute en ms (défaut: 500)", false),
                        // Commande Status Tracker
                        Commands.slash("statustrack", "Surveille le statut d'un utilisateur")
                                .addOption(OptionType.STRING, "action", "start/stop/status", true)
                                .addOption(OptionType.USER, "user", "L'utilisateur à surveiller (pour start)", false)
                                .addOption(OptionType.CHANNEL, "channel", "Le salon pour les notifications (pour start)", false),
                        // Commande Qui Exclusion
                        Commands.slash("quiexclude", "Exclure/inclure un utilisateur de 'Qui t'a demandé'")
                                .addOption(OptionType.STRING, "action", "add/remove/list", true)
                                .addOption(OptionType.USER, "user", "L'utilisateur à exclure/inclure", false),
                        // Commandes Casino
                        Commands.slash("balance", "Affiche ton solde ou celui d'un autre")
                                .addOption(OptionType.USER, "user", "L'utilisateur dont tu veux voir le solde", false),
                        Commands.slash("daily", "Récupère tes 🪙 quotidiennes!"),
                        Commands.slash("slots", "Joue à la machine à sous"),
                        Commands.slash("coinflip", "Pile ou Face"),
                        Commands.slash("blackjack", "Joue au Blackjack"),
                        Commands.slash("give", "Donne des 🪙 à quelqu'un")
                                .addOption(OptionType.USER, "user", "Destinataire", true)
                                .addOption(OptionType.INTEGER, "montant", "Montant à donner", true),
                        Commands.slash("leaderboard", "Classement des plus riches")
                ).queue();
            }

            System.out.println("Bot connecté en tant que: " + jda.getSelfUser().getName());
            System.out.println("Le bot est prêt!");

            // Arrêter proprement à la fermeture
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                randomMute.shutdown();
                ghostPing.shutdown();
                jda.shutdown();
            }));

            // Garder le bot en vie (bloque jusqu'à l'arrêt)
            jda.awaitShutdown();

        } catch (Exception e) {
            System.err.println("Erreur lors du démarrage du bot: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
