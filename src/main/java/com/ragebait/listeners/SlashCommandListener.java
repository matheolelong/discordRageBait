package com.ragebait.listeners;

import com.ragebait.CasinoManager;
import com.ragebait.ConfigManager;
import com.ragebait.GhostPingManager;
import com.ragebait.QuiExclusionManager;
import com.ragebait.RandomMuteManager;
import com.ragebait.StatusTrackerManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.awt.Color;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SlashCommandListener extends ListenerAdapter {

    private static final String[] RAGE_BAIT_MESSAGES = {
            "Je pense que les pizzas à l'ananas sont les meilleures 🍕🍍",
            "Star Wars épisode 8 est le meilleur de la saga 🎬",
            "Le lait avant les céréales, c'est la seule vraie façon 🥛",
            "Les chats sont clairement supérieurs aux chiens 🐱",
            "L'eau chaude est meilleure que l'eau froide 💧",
            "Windows est meilleur que Linux pour tout 💻",
            "Le PHP est le meilleur langage de programmation 🐘",
            "Les séries sont meilleures que les films 📺",
            "La France a la meilleure cuisine du monde 🇫🇷",
            "Le chocolat blanc est du vrai chocolat 🍫"
    };

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "ping" -> handlePing(event);
            case "ragebait" -> handleRageBait(event);
            case "info" -> handleInfo(event);
            case "ghostping" -> handleGhostPing(event);
            case "settarget" -> handleSetTarget(event);
            case "addchannel" -> handleAddChannel(event);
            case "removechannel" -> handleRemoveChannel(event);
            case "setinterval" -> handleSetInterval(event);
            case "randommute" -> handleRandomMute(event);
            case "statustrack" -> handleStatusTrack(event);
            case "quiexclude" -> handleQuiExclude(event);
            // Casino
            case "balance" -> handleBalance(event);
            case "daily" -> handleDaily(event);
            case "slots" -> handleSlots(event);
            case "coinflip" -> handleCoinflip(event);
            case "blackjack" -> handleBlackjack(event);
            case "give" -> handleGive(event);
            case "leaderboard" -> handleLeaderboard(event);
            default -> event.reply("Commande inconnue!").setEphemeral(true).queue();
        }
    }

    private void handlePing(SlashCommandInteractionEvent event) {
        long time = System.currentTimeMillis();
        event.reply("Pong!").queue(response -> {
            long ping = System.currentTimeMillis() - time;
            response.editOriginal("Pong! 🏓 Latence: " + ping + "ms | API: " + event.getJDA().getGatewayPing() + "ms").queue();
        });
    }

    private void handleRageBait(SlashCommandInteractionEvent event) {
        String customMessage = event.getOption("message") != null 
                ? event.getOption("message").getAsString() 
                : null;

        String messageToSend;
        if (customMessage != null && !customMessage.isEmpty()) {
            messageToSend = customMessage;
        } else {
            int randomIndex = (int) (Math.random() * RAGE_BAIT_MESSAGES.length);
            messageToSend = RAGE_BAIT_MESSAGES[randomIndex];
        }

        event.reply("🎣 **RAGE BAIT** 🎣\n\n" + messageToSend).queue();
    }

    private void handleInfo(SlashCommandInteractionEvent event) {
        GhostPingManager gp = GhostPingManager.getInstance();
        RandomMuteManager rm = RandomMuteManager.getInstance();
        StatusTrackerManager st = StatusTrackerManager.getInstance();
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎣 Rage Bait Bot")
                .setDescription("Un bot pour faire rager tes potes!")
                .setColor(Color.ORANGE)
                .addField("👻 Ghost Ping", """
                        `/ghostping start/stop/status`
                        `/settarget @user` - Cible à ping
                        `/addchannel #salon` - Ajouter salon
                        `/removechannel #salon` - Retirer salon
                        `/setinterval <min> [sec]` - Intervalle
                        """, false)
                .addField("🔇 Random Mute", """
                        `/randommute start @user [delai] [duree]`
                        `/randommute stop`
                        `/randommute status`
                        """, false)
                .addField("📝 Status Tracker", """
                        `/statustrack start @user #salon`
                        `/statustrack stop`
                        `/statustrack status`
                        """, false)
                .addField("🎰 Casino", """
                        `/balance` - Voir ton solde
                        `/daily` - Récupère 500 🪙 / 24h
                        `/slots <mise>` - Machine à sous
                        `/coinflip <mise> <pile/face>` - Pile ou Face
                        `/blackjack <mise>` - Blackjack
                        `/give @user <montant>` - Donner des 🪙
                        `/leaderboard` - Classement
                        """, false)
                .addField("📊 Statuts actuels", 
                        "Ghost Ping: " + (gp.isRunning() ? "🟢" : "🔴") + 
                        " | Random Mute: " + (rm.isRunning() ? "🟢" : "🔴") +
                        " | Status Tracker: " + (st.isEnabled() ? "🟢" : "🔴"), false)
                .setFooter("Made with JDA ❤️");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleGhostPing(SlashCommandInteractionEvent event) {
        String action = event.getOption("action").getAsString().toLowerCase();
        GhostPingManager gp = GhostPingManager.getInstance();

        switch (action) {
            case "start" -> {
                if (gp.getChannelIds().isEmpty()) {
                    event.reply("❌ Aucun salon défini! Utilise `/addchannel #salon`").setEphemeral(true).queue();
                    return;
                }
                gp.start();
                event.reply("👻 Ghost ping démarré! Intervalle: " + gp.getInterval() + " secondes").setEphemeral(true).queue();
            }
            case "stop" -> {
                gp.stop();
                event.reply("🛑 Ghost ping arrêté!").setEphemeral(true).queue();
            }
            case "status" -> {
                StringBuilder sb = new StringBuilder();
                sb.append("**Statut:** ").append(gp.isRunning() ? "🟢 Actif" : "🔴 Inactif").append("\n");
                sb.append("**Intervalle:** ").append(gp.getInterval()).append(" secondes\n");
                sb.append("**Cible:** ");
                if (gp.getTargetUserId() != null) {
                    sb.append("<@").append(gp.getTargetUserId()).append(">\n");
                } else {
                    sb.append("Non définie\n");
                }
                sb.append("**Salons:** ");
                Set<Long> channels = gp.getChannelIds();
                if (channels.isEmpty()) {
                    sb.append("Aucun");
                } else {
                    sb.append(channels.stream().map(id -> "<#" + id + ">").reduce((a, b) -> a + ", " + b).orElse(""));
                }
                event.reply(sb.toString()).setEphemeral(true).queue();
            }
            default -> event.reply("❌ Action invalide! Utilise: start, stop, status").setEphemeral(true).queue();
        }
    }

    private void handleSetTarget(SlashCommandInteractionEvent event) {
        User target = event.getOption("user").getAsUser();
        GhostPingManager gp = GhostPingManager.getInstance();
        gp.setTargetUser(target.getIdLong());
        ConfigManager.saveConfig(gp);
        event.reply("🎯 Cible définie: " + target.getAsMention()).setEphemeral(true).queue();
    }

    private void handleAddChannel(SlashCommandInteractionEvent event) {
        var channel = event.getOption("channel").getAsChannel();
        if (channel instanceof TextChannel) {
            GhostPingManager gp = GhostPingManager.getInstance();
            gp.addChannel(channel.getIdLong());
            ConfigManager.saveConfig(gp);
            event.reply("✅ Salon ajouté: <#" + channel.getIdLong() + ">").setEphemeral(true).queue();
        } else {
            event.reply("❌ Ce n'est pas un salon textuel!").setEphemeral(true).queue();
        }
    }

    private void handleRemoveChannel(SlashCommandInteractionEvent event) {
        var channel = event.getOption("channel").getAsChannel();
        GhostPingManager gp = GhostPingManager.getInstance();
        gp.removeChannel(channel.getIdLong());
        ConfigManager.saveConfig(gp);
        event.reply("✅ Salon retiré: <#" + channel.getIdLong() + ">").setEphemeral(true).queue();
    }

    private void handleSetInterval(SlashCommandInteractionEvent event) {
        int minutes = event.getOption("minutes").getAsInt();
        int seconds = event.getOption("secondes") != null ? event.getOption("secondes").getAsInt() : 0;
        
        int totalSeconds = (minutes * 60) + seconds;
        
        if (totalSeconds < 1) {
            event.reply("❌ L'intervalle doit être d'au moins 1 seconde!").setEphemeral(true).queue();
            return;
        }
        GhostPingManager gp = GhostPingManager.getInstance();
        gp.setInterval(totalSeconds);
        ConfigManager.saveConfig(gp);
        
        String display = "";
        if (minutes > 0) display += minutes + " min ";
        if (seconds > 0) display += seconds + "s";
        if (display.isEmpty()) display = totalSeconds + "s";
        
        event.reply("⏱️ Intervalle défini à " + display.trim()).setEphemeral(true).queue();
    }

    private void handleRandomMute(SlashCommandInteractionEvent event) {
        String action = event.getOption("action").getAsString().toLowerCase();
        RandomMuteManager rm = RandomMuteManager.getInstance();

        switch (action) {
            case "start" -> {
                var userOption = event.getOption("user");
                if (userOption == null) {
                    event.reply("❌ Tu dois spécifier un utilisateur! `/randommute start @user`").setEphemeral(true).queue();
                    return;
                }
                
                User target = userOption.getAsUser();
                int maxDelay = event.getOption("delai") != null ? event.getOption("delai").getAsInt() : 30;
                int muteDuration = event.getOption("duree") != null ? event.getOption("duree").getAsInt() : 500;
                
                if (maxDelay < 1) {
                    event.reply("❌ Le délai doit être d'au moins 1 seconde!").setEphemeral(true).queue();
                    return;
                }
                
                rm.setTarget(target.getIdLong(), event.getGuild().getIdLong());
                rm.setMaxDelay(maxDelay);
                rm.setMuteDuration(muteDuration);
                rm.setJda(event.getJDA());
                rm.start();
                
                event.reply("🔇 Random mute démarré!\n" +
                        "**Cible:** " + target.getAsMention() + "\n" +
                        "**Délai max:** " + maxDelay + "s\n" +
                        "**Durée mute:** " + muteDuration + "ms").setEphemeral(true).queue();
            }
            case "stop" -> {
                rm.stop();
                event.reply("🛑 Random mute arrêté!").setEphemeral(true).queue();
            }
            case "status" -> {
                StringBuilder sb = new StringBuilder();
                sb.append("**Statut:** ").append(rm.isRunning() ? "🟢 Actif" : "🔴 Inactif").append("\n");
                sb.append("**Délai max:** ").append(rm.getMaxDelay()).append(" secondes\n");
                sb.append("**Durée mute:** ").append(rm.getMuteDuration()).append("ms\n");
                sb.append("**Cible:** ");
                if (rm.getTargetUserId() != null) {
                    sb.append("<@").append(rm.getTargetUserId()).append(">");
                } else {
                    sb.append("Non définie");
                }
                event.reply(sb.toString()).setEphemeral(true).queue();
            }
            default -> event.reply("❌ Action invalide! Utilise: start, stop, status").setEphemeral(true).queue();
        }
    }

    private void handleStatusTrack(SlashCommandInteractionEvent event) {
        String action = event.getOption("action").getAsString().toLowerCase();
        StatusTrackerManager st = StatusTrackerManager.getInstance();

        switch (action) {
            case "start" -> {
                var userOption = event.getOption("user");
                var channelOption = event.getOption("channel");
                
                if (userOption == null) {
                    event.reply("❌ Tu dois spécifier un utilisateur! `/statustrack start @user #salon`").setEphemeral(true).queue();
                    return;
                }
                
                if (channelOption == null) {
                    event.reply("❌ Tu dois spécifier un salon! `/statustrack start @user #salon`").setEphemeral(true).queue();
                    return;
                }
                
                var channel = channelOption.getAsChannel();
                if (!(channel instanceof TextChannel textChannel)) {
                    event.reply("❌ Ce n'est pas un salon textuel!").setEphemeral(true).queue();
                    return;
                }
                
                User target = userOption.getAsUser();
                
                st.setTarget(target.getIdLong(), event.getGuild().getIdLong());
                st.setNotificationChannel(textChannel.getIdLong());
                st.setJda(event.getJDA());
                st.enable();
                
                event.reply("👀 Status tracker activé!\n" +
                        "**Cible:** " + target.getAsMention() + "\n" +
                        "**Notifications dans:** " + textChannel.getAsMention()).setEphemeral(true).queue();
            }
            case "stop" -> {
                st.disable();
                event.reply("🛑 Status tracker arrêté!").setEphemeral(true).queue();
            }
            case "status" -> {
                StringBuilder sb = new StringBuilder();
                sb.append("**Statut:** ").append(st.isEnabled() ? "🟢 Actif" : "🔴 Inactif").append("\n");
                sb.append("**Cible:** ");
                if (st.getTargetUserId() != null) {
                    sb.append("<@").append(st.getTargetUserId()).append(">\n");
                } else {
                    sb.append("Non définie\n");
                }
                sb.append("**Salon notifications:** ");
                if (st.getNotificationChannelId() != null) {
                    sb.append("<#").append(st.getNotificationChannelId()).append(">");
                } else {
                    sb.append("Non défini");
                }
                event.reply(sb.toString()).setEphemeral(true).queue();
            }
            default -> event.reply("❌ Action invalide! Utilise: start, stop, status").setEphemeral(true).queue();
        }
    }

    private void handleQuiExclude(SlashCommandInteractionEvent event) {
        String action = event.getOption("action").getAsString().toLowerCase();
        QuiExclusionManager qe = QuiExclusionManager.getInstance();

        switch (action) {
            case "add" -> {
                var userOption = event.getOption("user");
                if (userOption == null) {
                    event.reply("❌ Tu dois spécifier un utilisateur! `/quiexclude add @user`").setEphemeral(true).queue();
                    return;
                }
                
                User target = userOption.getAsUser();
                qe.addExclusion(target.getIdLong());
                ConfigManager.saveQuiExclusions(qe);
                event.reply("✅ " + target.getAsMention() + " est maintenant exclu de 'Qui t'a demandé'").setEphemeral(true).queue();
            }
            case "remove" -> {
                var userOption = event.getOption("user");
                if (userOption == null) {
                    event.reply("❌ Tu dois spécifier un utilisateur! `/quiexclude remove @user`").setEphemeral(true).queue();
                    return;
                }
                
                User target = userOption.getAsUser();
                qe.removeExclusion(target.getIdLong());
                ConfigManager.saveQuiExclusions(qe);
                event.reply("✅ " + target.getAsMention() + " n'est plus exclu de 'Qui t'a demandé'").setEphemeral(true).queue();
            }
            case "list" -> {
                Set<Long> excluded = qe.getExcludedUsers();
                if (excluded.isEmpty()) {
                    event.reply("📋 Aucun utilisateur exclu").setEphemeral(true).queue();
                } else {
                    StringBuilder sb = new StringBuilder("📋 **Utilisateurs exclus:**\n");
                    for (Long userId : excluded) {
                        sb.append("- <@").append(userId).append(">\n");
                    }
                    event.reply(sb.toString()).setEphemeral(true).queue();
                }
            }
            default -> event.reply("❌ Action invalide! Utilise: add, remove, list").setEphemeral(true).queue();
        }
    }
    
    // ============ CASINO COMMANDS ============
    
    // Stockage temporaire des mises et des parties
    private static final Map<Long, Long> lastBets = new ConcurrentHashMap<>();
    private static final Map<Long, CasinoManager.BlackjackGame> activeGames = new ConcurrentHashMap<>();
    
    private void handleBalance(SlashCommandInteractionEvent event) {
        CasinoManager casino = CasinoManager.getInstance();
        var userOption = event.getOption("user");
        User target = userOption != null ? userOption.getAsUser() : event.getUser();
        
        long balance = casino.getBalance(target.getIdLong());
        
        if (target.equals(event.getUser())) {
            event.reply("💰 Tu as **" + balance + "** 🪙").queue();
        } else {
            event.reply("💰 " + target.getAsMention() + " a **" + balance + "** 🪙").queue();
        }
    }
    
    private void handleDaily(SlashCommandInteractionEvent event) {
        CasinoManager casino = CasinoManager.getInstance();
        var result = casino.claimDaily(event.getUser().getIdLong());
        
        if (result.success()) {
            long newBalance = casino.getBalance(event.getUser().getIdLong());
            event.reply("🎁 Tu as récupéré **" + result.amount() + "** 🪙!\n💰 Nouveau solde: **" + newBalance + "** 🪙").queue();
        } else {
            long hours = result.cooldownRemaining() / 3600;
            long minutes = (result.cooldownRemaining() % 3600) / 60;
            event.reply("⏰ Tu dois attendre encore **" + hours + "h " + minutes + "m** avant ton prochain daily!").setEphemeral(true).queue();
        }
    }
    
    private void handleSlots(SlashCommandInteractionEvent event) {
        CasinoManager casino = CasinoManager.getInstance();
        long balance = casino.getBalance(event.getUser().getIdLong());
        
        TextInput betInput = TextInput.create("bet", "Mise", TextInputStyle.SHORT)
                .setPlaceholder("Ex: 100")
                .setMinLength(1)
                .setMaxLength(10)
                .setRequired(true)
                .build();
        
        Modal modal = Modal.create("slots_modal", "🎰 Machine à Sous")
                .addActionRow(betInput)
                .build();
        
        event.replyModal(modal).queue();
    }
    
    private void handleCoinflip(SlashCommandInteractionEvent event) {
        TextInput betInput = TextInput.create("bet", "Mise", TextInputStyle.SHORT)
                .setPlaceholder("Ex: 100")
                .setMinLength(1)
                .setMaxLength(10)
                .setRequired(true)
                .build();
        
        TextInput choiceInput = TextInput.create("choice", "Choix (pile ou face)", TextInputStyle.SHORT)
                .setPlaceholder("pile ou face")
                .setMinLength(1)
                .setMaxLength(5)
                .setRequired(true)
                .build();
        
        Modal modal = Modal.create("coinflip_modal", "🎲 Pile ou Face")
                .addActionRow(betInput)
                .addActionRow(choiceInput)
                .build();
        
        event.replyModal(modal).queue();
    }
    
    private void handleBlackjack(SlashCommandInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        
        if (activeGames.containsKey(userId)) {
            event.reply("❌ Tu as déjà une partie en cours! Utilise les boutons pour jouer.").setEphemeral(true).queue();
            return;
        }
        
        TextInput betInput = TextInput.create("bet", "Mise", TextInputStyle.SHORT)
                .setPlaceholder("Ex: 100")
                .setMinLength(1)
                .setMaxLength(10)
                .setRequired(true)
                .build();
        
        Modal modal = Modal.create("blackjack_modal", "🃏 Blackjack")
                .addActionRow(betInput)
                .build();
        
        event.replyModal(modal).queue();
    }
    
    // ============ MODAL HANDLER ============
    
    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        switch (event.getModalId()) {
            case "slots_modal" -> handleSlotsModal(event);
            case "coinflip_modal" -> handleCoinflipModal(event);
            case "blackjack_modal" -> handleBlackjackModal(event);
        }
    }
    
    private void handleSlotsModal(ModalInteractionEvent event) {
        CasinoManager casino = CasinoManager.getInstance();
        long userId = event.getUser().getIdLong();
        
        String betStr = event.getValue("bet").getAsString().trim();
        long bet;
        try {
            bet = Long.parseLong(betStr);
        } catch (NumberFormatException e) {
            event.reply("❌ Mise invalide! Entre un nombre.").setEphemeral(true).queue();
            return;
        }
        
        var result = casino.playSlots(userId, bet);
        
        if (!result.played()) {
            event.reply(result.message()).setEphemeral(true).queue();
            return;
        }
        
        lastBets.put(userId, bet);
        String[] s = result.symbols();
        StringBuilder sb = new StringBuilder();
        sb.append("🎰 **SLOTS** 🎰\n");
        sb.append("Mise: **").append(bet).append("** 🪙\n\n");
        sb.append("╔═══════════╗\n");
        sb.append("║ ").append(s[0]).append(" ║ ").append(s[1]).append(" ║ ").append(s[2]).append(" ║\n");
        sb.append("╚═══════════╝\n\n");
        sb.append(result.message()).append("\n");
        
        if (result.winnings() > 0) {
            sb.append("💵 Gains: **+").append(result.winnings()).append("** 🪙\n");
        } else {
            sb.append("💸 Perdu: **-").append(bet).append("** 🪙\n");
        }
        sb.append("💰 Solde: **").append(result.newBalance()).append("** 🪙");
        
        event.reply(sb.toString())
            .addActionRow(
                Button.success("slots_replay", "🔄 Rejouer (même mise)"),
                Button.primary("slots_new", "🎰 Nouvelle mise")
            ).queue();
    }
    
    private void handleCoinflipModal(ModalInteractionEvent event) {
        CasinoManager casino = CasinoManager.getInstance();
        long userId = event.getUser().getIdLong();
        
        String betStr = event.getValue("bet").getAsString().trim();
        String choice = event.getValue("choice").getAsString().trim().toLowerCase();
        
        long bet;
        try {
            bet = Long.parseLong(betStr);
        } catch (NumberFormatException e) {
            event.reply("❌ Mise invalide! Entre un nombre.").setEphemeral(true).queue();
            return;
        }
        
        boolean chooseHeads;
        if (choice.equals("pile") || choice.equals("p")) {
            chooseHeads = true;
        } else if (choice.equals("face") || choice.equals("f")) {
            chooseHeads = false;
        } else {
            event.reply("❌ Choix invalide! Utilise 'pile' ou 'face'").setEphemeral(true).queue();
            return;
        }
        
        var result = casino.playCoinflip(userId, bet, chooseHeads);
        
        if (!result.played()) {
            event.reply(result.message()).setEphemeral(true).queue();
            return;
        }
        
        lastBets.put(userId, bet);
        String coinResult = result.wasHeads() ? "🪙 **PILE**" : "💿 **FACE**";
        StringBuilder sb = new StringBuilder();
        sb.append("🎲 **COINFLIP** 🎲\n");
        sb.append("Mise: **").append(bet).append("** 🪙\n\n");
        sb.append("Tu as choisi: **").append(chooseHeads ? "Pile" : "Face").append("**\n");
        sb.append("Résultat: ").append(coinResult).append("\n\n");
        sb.append(result.message()).append("\n");
        sb.append("💰 Solde: **").append(result.newBalance()).append("** 🪙");
        
        event.reply(sb.toString())
            .addActionRow(
                Button.success("coinflip_replay_" + (chooseHeads ? "pile" : "face"), "🔄 Rejouer (même mise)"),
                Button.primary("coinflip_new", "🎲 Nouvelle mise")
            ).queue();
    }
    
    private void handleBlackjackModal(ModalInteractionEvent event) {
        CasinoManager casino = CasinoManager.getInstance();
        long userId = event.getUser().getIdLong();
        
        String betStr = event.getValue("bet").getAsString().trim();
        long bet;
        try {
            bet = Long.parseLong(betStr);
        } catch (NumberFormatException e) {
            event.reply("❌ Mise invalide! Entre un nombre.").setEphemeral(true).queue();
            return;
        }
        
        if (bet <= 0) {
            event.reply("❌ La mise doit être positive!").setEphemeral(true).queue();
            return;
        }
        
        if (casino.getBalance(userId) < bet) {
            event.reply("❌ Tu n'as pas assez de 🪙! (Solde: " + casino.getBalance(userId) + ")").setEphemeral(true).queue();
            return;
        }
        
        if (activeGames.containsKey(userId)) {
            event.reply("❌ Tu as déjà une partie en cours!").setEphemeral(true).queue();
            return;
        }
        
        var game = casino.startBlackjack(userId, bet);
        if (game == null) {
            event.reply("❌ Impossible de démarrer la partie!").setEphemeral(true).queue();
            return;
        }
        
        activeGames.put(userId, game);
        lastBets.put(userId, bet);
        
        if (game.isGameOver()) {
            casino.finishBlackjack(game, game.getWinnings());
            activeGames.remove(userId);
            event.reply(buildBlackjackMessage(game, true))
                .addActionRow(
                    Button.success("blackjack_replay", "🔄 Rejouer (même mise)"),
                    Button.primary("blackjack_new", "🃏 Nouvelle mise")
                ).queue();
        } else {
            event.reply(buildBlackjackMessage(game, false))
                .addActionRow(
                    Button.primary("bj_hit", "🃏 Hit"),
                    Button.danger("bj_stand", "✋ Stand")
                ).queue();
        }
    }
    
    private String buildBlackjackMessage(CasinoManager.BlackjackGame game, boolean gameOver) {
        StringBuilder sb = new StringBuilder();
        sb.append("🃏 **BLACKJACK** 🃏\n");
        sb.append("Mise: **").append(game.getBet()).append("** 🪙\n\n");
        sb.append("**Dealer:** ").append(game.getDealerHand(gameOver)).append("\n");
        sb.append("**Toi:** ").append(game.getPlayerHand()).append("\n\n");
        
        if (gameOver) {
            sb.append(game.getResult()).append("\n");
            long newBalance = CasinoManager.getInstance().getBalance(game.getUserId());
            if (game.getWinnings() > 0) {
                sb.append("💵 Gains: **+").append(game.getWinnings() - game.getBet()).append("** 🪙\n");
            } else {
                sb.append("💸 Perdu: **-").append(game.getBet()).append("** 🪙\n");
            }
            sb.append("💰 Solde: **").append(newBalance).append("** 🪙");
        } else {
            sb.append("*Hit pour tirer, Stand pour rester*");
        }
        
        return sb.toString();
    }
    
    // ============ BUTTON HANDLER ============
    
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        long userId = event.getUser().getIdLong();
        CasinoManager casino = CasinoManager.getInstance();
        
        // Blackjack Hit/Stand
        if (buttonId.equals("bj_hit") || buttonId.equals("bj_stand")) {
            var game = activeGames.get(userId);
            if (game == null) {
                event.reply("❌ Tu n'as pas de partie en cours!").setEphemeral(true).queue();
                return;
            }
            
            if (buttonId.equals("bj_hit")) {
                game.hit();
                if (game.isGameOver()) {
                    casino.finishBlackjack(game, game.getWinnings());
                    activeGames.remove(userId);
                    event.editMessage(buildBlackjackMessage(game, true))
                        .setActionRow(
                            Button.success("blackjack_replay", "🔄 Rejouer (même mise)"),
                            Button.primary("blackjack_new", "🃏 Nouvelle mise")
                        ).queue();
                } else {
                    event.editMessage(buildBlackjackMessage(game, false)).queue();
                }
            } else {
                game.stand();
                casino.finishBlackjack(game, game.getWinnings());
                activeGames.remove(userId);
                event.editMessage(buildBlackjackMessage(game, true))
                    .setActionRow(
                        Button.success("blackjack_replay", "🔄 Rejouer (même mise)"),
                        Button.primary("blackjack_new", "🃏 Nouvelle mise")
                    ).queue();
            }
            return;
        }
        
        // Slots replay
        if (buttonId.equals("slots_replay")) {
            Long lastBet = lastBets.get(userId);
            if (lastBet == null) {
                event.reply("❌ Aucune mise précédente trouvée!").setEphemeral(true).queue();
                return;
            }
            
            var result = casino.playSlots(userId, lastBet);
            if (!result.played()) {
                event.reply(result.message()).setEphemeral(true).queue();
                return;
            }
            
            String[] s = result.symbols();
            StringBuilder sb = new StringBuilder();
            sb.append("🎰 **SLOTS** 🎰\n");
            sb.append("Mise: **").append(lastBet).append("** 🪙\n\n");
            sb.append("╔═══════════╗\n");
            sb.append("║ ").append(s[0]).append(" ║ ").append(s[1]).append(" ║ ").append(s[2]).append(" ║\n");
            sb.append("╚═══════════╝\n\n");
            sb.append(result.message()).append("\n");
            
            if (result.winnings() > 0) {
                sb.append("💵 Gains: **+").append(result.winnings()).append("** 🪙\n");
            } else {
                sb.append("💸 Perdu: **-").append(lastBet).append("** 🪙\n");
            }
            sb.append("💰 Solde: **").append(result.newBalance()).append("** 🪙");
            
            event.editMessage(sb.toString()).queue();
            return;
        }
        
        // Slots new bet
        if (buttonId.equals("slots_new")) {
            TextInput betInput = TextInput.create("bet", "Mise", TextInputStyle.SHORT)
                    .setPlaceholder("Ex: 100")
                    .setMinLength(1)
                    .setMaxLength(10)
                    .setRequired(true)
                    .build();
            
            Modal modal = Modal.create("slots_modal", "🎰 Machine à Sous")
                    .addActionRow(betInput)
                    .build();
            
            event.replyModal(modal).queue();
            return;
        }
        
        // Coinflip replay
        if (buttonId.startsWith("coinflip_replay_")) {
            Long lastBet = lastBets.get(userId);
            if (lastBet == null) {
                event.reply("❌ Aucune mise précédente trouvée!").setEphemeral(true).queue();
                return;
            }
            
            boolean chooseHeads = buttonId.endsWith("pile");
            var result = casino.playCoinflip(userId, lastBet, chooseHeads);
            
            if (!result.played()) {
                event.reply(result.message()).setEphemeral(true).queue();
                return;
            }
            
            String coinResult = result.wasHeads() ? "🪙 **PILE**" : "💿 **FACE**";
            StringBuilder sb = new StringBuilder();
            sb.append("🎲 **COINFLIP** 🎲\n");
            sb.append("Mise: **").append(lastBet).append("** 🪙\n\n");
            sb.append("Tu as choisi: **").append(chooseHeads ? "Pile" : "Face").append("**\n");
            sb.append("Résultat: ").append(coinResult).append("\n\n");
            sb.append(result.message()).append("\n");
            sb.append("💰 Solde: **").append(result.newBalance()).append("** 🪙");
            
            event.editMessage(sb.toString()).queue();
            return;
        }
        
        // Coinflip new bet
        if (buttonId.equals("coinflip_new")) {
            TextInput betInput = TextInput.create("bet", "Mise", TextInputStyle.SHORT)
                    .setPlaceholder("Ex: 100")
                    .setMinLength(1)
                    .setMaxLength(10)
                    .setRequired(true)
                    .build();
            
            TextInput choiceInput = TextInput.create("choice", "Choix (pile ou face)", TextInputStyle.SHORT)
                    .setPlaceholder("pile ou face")
                    .setMinLength(1)
                    .setMaxLength(5)
                    .setRequired(true)
                    .build();
            
            Modal modal = Modal.create("coinflip_modal", "🎲 Pile ou Face")
                    .addActionRow(betInput)
                    .addActionRow(choiceInput)
                    .build();
            
            event.replyModal(modal).queue();
            return;
        }
        
        // Blackjack replay
        if (buttonId.equals("blackjack_replay")) {
            Long lastBet = lastBets.get(userId);
            if (lastBet == null) {
                event.reply("❌ Aucune mise précédente trouvée!").setEphemeral(true).queue();
                return;
            }
            
            if (activeGames.containsKey(userId)) {
                event.reply("❌ Tu as déjà une partie en cours!").setEphemeral(true).queue();
                return;
            }
            
            if (casino.getBalance(userId) < lastBet) {
                event.reply("❌ Tu n'as pas assez de 🪙! (Solde: " + casino.getBalance(userId) + ")").setEphemeral(true).queue();
                return;
            }
            
            var game = casino.startBlackjack(userId, lastBet);
            if (game == null) {
                event.reply("❌ Impossible de démarrer la partie!").setEphemeral(true).queue();
                return;
            }
            
            activeGames.put(userId, game);
            
            if (game.isGameOver()) {
                casino.finishBlackjack(game, game.getWinnings());
                activeGames.remove(userId);
                event.editMessage(buildBlackjackMessage(game, true))
                    .setActionRow(
                        Button.success("blackjack_replay", "🔄 Rejouer (même mise)"),
                        Button.primary("blackjack_new", "🃏 Nouvelle mise")
                    ).queue();
            } else {
                event.editMessage(buildBlackjackMessage(game, false))
                    .setActionRow(
                        Button.primary("bj_hit", "🃏 Hit"),
                        Button.danger("bj_stand", "✋ Stand")
                    ).queue();
            }
            return;
        }
        
        // Blackjack new bet
        if (buttonId.equals("blackjack_new")) {
            if (activeGames.containsKey(userId)) {
                event.reply("❌ Tu as déjà une partie en cours!").setEphemeral(true).queue();
                return;
            }
            
            TextInput betInput = TextInput.create("bet", "Mise", TextInputStyle.SHORT)
                    .setPlaceholder("Ex: 100")
                    .setMinLength(1)
                    .setMaxLength(10)
                    .setRequired(true)
                    .build();
            
            Modal modal = Modal.create("blackjack_modal", "🃏 Blackjack")
                    .addActionRow(betInput)
                    .build();
            
            event.replyModal(modal).queue();
        }
    }
    
    private void handleGive(SlashCommandInteractionEvent event) {
        CasinoManager casino = CasinoManager.getInstance();
        User target = event.getOption("user").getAsUser();
        long amount = event.getOption("montant").getAsLong();
        
        if (target.isBot()) {
            event.reply("❌ Tu ne peux pas donner de 🪙 à un bot!").setEphemeral(true).queue();
            return;
        }
        
        if (target.getIdLong() == event.getUser().getIdLong()) {
            event.reply("❌ Tu ne peux pas te donner de 🪙 à toi-même!").setEphemeral(true).queue();
            return;
        }
        
        if (amount <= 0) {
            event.reply("❌ Le montant doit être positif!").setEphemeral(true).queue();
            return;
        }
        
        if (casino.transfer(event.getUser().getIdLong(), target.getIdLong(), amount)) {
            event.reply("✅ Tu as donné **" + amount + "** 🪙 à " + target.getAsMention() + "\n💰 Ton solde: **" + casino.getBalance(event.getUser().getIdLong()) + "** 🪙").queue();
        } else {
            event.reply("❌ Tu n'as pas assez de 🪙!").setEphemeral(true).queue();
        }
    }
    
    private void handleLeaderboard(SlashCommandInteractionEvent event) {
        CasinoManager casino = CasinoManager.getInstance();
        var leaderboard = casino.getLeaderboard(10);
        
        if (leaderboard.isEmpty()) {
            event.reply("📊 Aucun joueur n'a encore de 🪙!").queue();
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("🏆 **CLASSEMENT** 🏆\n\n");
        
        int rank = 1;
        for (var entry : leaderboard) {
            String medal = switch (rank) {
                case 1 -> "🥇";
                case 2 -> "🥈";
                case 3 -> "🥉";
                default -> "**" + rank + ".**";
            };
            sb.append(medal).append(" <@").append(entry.getKey()).append("> - **").append(entry.getValue()).append("** 🪙\n");
            rank++;
        }
        
        event.reply(sb.toString()).queue();
    }
}
