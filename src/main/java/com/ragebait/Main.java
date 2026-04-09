package com.ragebait;

import com.ragebait.listeners.MessageListener;
import com.ragebait.listeners.PresenceListener;
import com.ragebait.listeners.SlashCommandListener;
import com.ragebait.cases.CaseManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // RÃ©cupÃ¨re le token depuis les variables d'environnement ou les arguments
        String token = System.getenv("DISCORD_TOKEN");
        if (token == null && args.length > 0) {
            token = args[0];
        }
        if (token == null) {
            log.error("Token Discord non trouvÃ©! DÃ©finissez DISCORD_TOKEN ou passez-le en argument.");
            System.exit(1);
        }

        log.info("=== DÃ©marrage du bot RageBait ===");

        // Initialiser la base de donnÃ©es PostgreSQL
        DatabaseManager.getInstance();

        try {
            // CrÃ©ation du bot avec JDA
            JDA jda = JDABuilder.createDefault(token)
                    // Intents nÃ©cessaires pour lire les messages, le vocal et les prÃ©sences
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES,
                                   GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_PRESENCES,
                                   GatewayIntent.GUILD_MEMBERS)
                    // Cache tous les membres pour recevoir les Ã©vÃ©nements de prÃ©sence
                    .setMemberCachePolicy(net.dv8tion.jda.api.utils.MemberCachePolicy.ALL)
                    .enableCache(net.dv8tion.jda.api.utils.cache.CacheFlag.ONLINE_STATUS,
                                 net.dv8tion.jda.api.utils.cache.CacheFlag.ACTIVITY)
                    // Statut affichÃ© sur Discord
                    .setActivity(Activity.playing("Rage Bait ðŸŽ£"))
                    // Ajout des listeners
                    .addEventListeners(new MessageListener())
                    .addEventListeners(new SlashCommandListener())
                    .addEventListeners(new PresenceListener())
                    .build();

            // Attendre que le bot soit prÃªt
            jda.awaitReady();
            log.info("Bot connectÃ© en tant que: {}", jda.getSelfUser().getName());

            // Initialiser le GhostPingManager
            GhostPingManager ghostPing = GhostPingManager.getInstance();
            ghostPing.setJda(jda);

                        // Initialiser le VoiceRewardManager
                        VoiceRewardManager voiceReward = VoiceRewardManager.getInstance();
                        voiceReward.setJda(jda);
                        voiceReward.start();

            // Initialiser le RandomMuteManager
            RandomMuteManager randomMute = RandomMuteManager.getInstance();
            randomMute.setJda(jda);

            // Initialiser le StatusTrackerManager
            StatusTrackerManager statusTracker = StatusTrackerManager.getInstance();
                        statusTracker.setJda(jda);
                        // Charger le status tracker pour chaque serveur
                        for (var guild : jda.getGuilds()) {
                                statusTracker.loadFromDb(guild.getIdLong());
                                // Si le tracking est activÃ©, on le relance
                                if (statusTracker.isEnabled()) {
                                        statusTracker.enable();
                                }
                        }

            // Initialiser le CasinoManager (donnÃ©es directement dans PostgreSQL)
            CasinoManager.getInstance();

            // Initialiser le CaseManager (charge cases/cases.json + crÃ©e la table d'inventaire)
            CaseManager.getInstance();
            log.info("SystÃ¨me de caisses initialisÃ©.");

            // Initialiser le RouletteManager
            RouletteManager roulette = RouletteManager.getInstance();
            roulette.setJda(jda);

            // Charger la configuration sauvegardÃ©e
            ConfigManager.loadConfig(ghostPing);

            // Enregistrer les commandes slash pour chaque serveur (mise Ã  jour instantanÃ©e)
            for (var guild : jda.getGuilds()) {
                guild.updateCommands().addCommands(
                        Commands.slash("ping", "RÃ©pond avec Pong!"),
                        Commands.slash("ragebait", "Envoie un message provocateur")
                                .addOption(OptionType.STRING, "message", "Le message Ã  envoyer", false),
                        Commands.slash("info", "Affiche les informations du bot"),
                        // Commandes Ghost Ping
                        Commands.slash("ghostping", "GÃ¨re le ghost ping automatique")
                                .addOption(OptionType.STRING, "action", "start/stop/status", true),
                        Commands.slash("settarget", "DÃ©finit la cible du ghost ping")
                                .addOption(OptionType.USER, "user", "L'utilisateur Ã  ping", true),
                        Commands.slash("addchannel", "Ajoute un salon pour le ghost ping")
                                .addOption(OptionType.CHANNEL, "channel", "Le salon Ã  ajouter", true),
                        Commands.slash("removechannel", "Retire un salon du ghost ping")
                                .addOption(OptionType.CHANNEL, "channel", "Le salon Ã  retirer", true),
                        Commands.slash("setinterval", "Change l'intervalle entre les ghost pings")
                                .addOption(OptionType.INTEGER, "minutes", "Intervalle en minutes", true)
                                .addOption(OptionType.INTEGER, "secondes", "Secondes supplÃ©mentaires", false),
                        // Commande Random Mute
                        Commands.slash("randommute", "Mute/deafen alÃ©atoire sur une cible")
                                .addOption(OptionType.STRING, "action", "start/stop/status", true)
                                .addOption(OptionType.USER, "user", "L'utilisateur Ã  mute (pour start)", false)
                                .addOption(OptionType.INTEGER, "delai", "DÃ©lai max en secondes (dÃ©faut: 30)", false)
                                .addOption(OptionType.INTEGER, "duree", "DurÃ©e du mute en ms (dÃ©faut: 500)", false),
                        // Commande Status Tracker
                        Commands.slash("statustrack", "Surveille le statut d'un utilisateur")
                                .addOption(OptionType.STRING, "action", "start/stop/status", true)
                                .addOption(OptionType.USER, "user", "L'utilisateur Ã  surveiller (pour start)", false)
                                .addOption(OptionType.CHANNEL, "channel", "Le salon pour les notifications (pour start)", false),
                        // Commande Qui Exclusion
                        Commands.slash("quiexclude", "Exclure/inclure un utilisateur de 'Qui t'a demandÃ©'")
                                .addOption(OptionType.STRING, "action", "add/remove/list", true)
                                .addOption(OptionType.USER, "user", "L'utilisateur Ã  exclure/inclure", false),
                        // Commandes Casino
                        Commands.slash("balance", "Affiche ton solde ou celui d'un autre")
                                .addOption(OptionType.USER, "user", "L'utilisateur dont tu veux voir le solde", false),
                        Commands.slash("daily", "RÃ©cupÃ¨re tes ðŸª™ quotidiennes!"),
                        Commands.slash("slots", "Joue Ã  la machine Ã  sous"),
                        Commands.slash("coinflip", "Pile ou Face"),
                        Commands.slash("blackjack", "Joue au Blackjack"),
                        Commands.slash("give", "Donne des ðŸª™ Ã  quelqu'un")
                                .addOption(OptionType.USER, "user", "Destinataire", true)
                                .addOption(OptionType.INTEGER, "montant", "Montant Ã  donner", true),
                        Commands.slash("leaderboard", "Classement des plus riches"),
                        // Roulette
                        Commands.slash("roulette", "GÃ¨re la roulette")
                                .addOption(OptionType.STRING, "action", "start/stop/status/delay", true)
                                .addOption(OptionType.CHANNEL, "channel", "Salon pour la roulette (pour start)", false)
                                .addOption(OptionType.INTEGER, "secondes", "DÃ©lai entre les tours (pour delay)", false),
                        Commands.slash("bet", "Place un pari Ã  la roulette")
                                .addOption(OptionType.INTEGER, "montant", "Montant Ã  miser", true)
                                .addOption(OptionType.STRING, "type", "Type de pari (rouge/noir/pair/impair/0-36...)", true),
                        Commands.slash("mybets", "Voir tes paris en cours \u00e0 la roulette"),
                        // Caisses CS:GO-like
                        Commands.slash("cases", "Liste des caisses disponibles"),
                        Commands.slash("buycase", "Achete une caisse")
                                .addOption(OptionType.STRING, "casename", "Nom de la caisse (ex: alpha_case)", true),
                        Commands.slash("opencase", "Ouvre une caisse de ton inventaire")
                                .addOption(OptionType.STRING, "casename", "Nom de la caisse a ouvrir", true),
                        Commands.slash("inventory", "Voir les caisses dans ton inventaire")
                ).queue();
                log.info("Commandes slash enregistrÃ©es pour le serveur: {}", guild.getName());
            }

            log.info("Bot prÃªt!");

                        // ArrÃªter proprement Ã  la fermeture
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                                log.info("ArrÃªt du bot en cours...");
                                randomMute.shutdown();
                                ghostPing.shutdown();
                                roulette.shutdown();
                                voiceReward.shutdown();
                                jda.shutdown();
                                log.info("Bot arrÃªtÃ©.");
                        }));

            // Garder le bot en vie (bloque jusqu'Ã  l'arrÃªt)
            jda.awaitShutdown();

        } catch (Exception e) {
            log.error("Erreur lors du dÃ©marrage du bot", e);
        }
    }
}
