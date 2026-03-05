package com.ragebait;

import com.ragebait.listeners.MessageListener;
import com.ragebait.listeners.SlashCommandListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Main {

    public static void main(String[] args) {
        // Récupère le token depuis les variables d'environnement ou les arguments
        String token = System.getenv("DISCORD_TOKEN");
        
        if (token == null && args.length > 0) {
            token = args[0];
        }
        
        if (token == null) {
            System.err.println("Erreur: Token Discord non trouvé!");
            System.err.println("Définissez la variable d'environnement DISCORD_TOKEN ou passez le token en argument.");
            System.exit(1);
        }

        try {
            // Création du bot avec JDA
            JDA jda = JDABuilder.createDefault(token)
                    // Intents nécessaires pour lire les messages
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                    // Statut affiché sur Discord
                    .setActivity(Activity.playing("Rage Bait 🎣"))
                    // Ajout des listeners
                    .addEventListeners(new MessageListener())
                    .addEventListeners(new SlashCommandListener())
                    .build();

            // Attendre que le bot soit prêt
            jda.awaitReady();

            // Initialiser le GhostPingManager
            GhostPingManager ghostPing = GhostPingManager.getInstance();
            ghostPing.setJda(jda);
            
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
                                .addOption(OptionType.INTEGER, "secondes", "Secondes supplémentaires", false)
                ).queue();
            }

            System.out.println("Bot connecté en tant que: " + jda.getSelfUser().getName());
            System.out.println("Le bot est prêt!");

            // Arrêter proprement le ghost ping à la fermeture
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                ghostPing.shutdown();
                jda.shutdown();
            }));

        } catch (Exception e) {
            System.err.println("Erreur lors du démarrage du bot: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
