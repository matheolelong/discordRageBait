package com.ragebait.listeners;

import com.ragebait.CasinoManager;
import com.ragebait.ConfigManager;
import com.ragebait.GhostPingManager;
import com.ragebait.QuiExclusionManager;
import com.ragebait.RandomMuteManager;
import com.ragebait.RouletteManager;
import com.ragebait.StatusTrackerManager;
import com.ragebait.cases.CaseCommandHandler;
import com.ragebait.cases.CaseInteractionHandler;
import com.ragebait.cases.CaseManager;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SlashCommandListener extends ListenerAdapter {

    /** Handler pour les commandes de caisses CS:GO-like */
    private final CaseCommandHandler caseHandler = new CaseCommandHandler();

    /** Handler pour les boutons interactifs des caisses et du shop */
    private final CaseInteractionHandler caseInteractionHandler = new CaseInteractionHandler(caseHandler);

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
            // Roulette
            case "roulette" -> handleRoulette(event);
            case "bet" -> handleBet(event);
            case "mybets" -> handleMyBets(event);
            // Caisses CS:GO-like
            case "cases" -> handleCases(event);
            case "buycase" -> handleBuyCase(event);
            case "opencase" -> handleOpenCase(event);
            case "inventory" -> handleInventory(event);
            case "weapons" -> handleWeapons(event);
            case "sell" -> handleSell(event);
            default -> event.reply("Commande inconnue!").setEphemeral(true).queue();
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        // Delegue les boutons interactifs de caisses/shop
        if (caseInteractionHandler.handle(event)) return;

        // Boutons existants (blackjack, slots, coinflip...)
        String id = event.getComponentId();
        // Les autres boutons sont traites par leurs handlers repectifs via les callbacks
        // (pas geres ici car ils utilisent des reply directement dans leurs flows)
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
        event.deferReply().setEphemeral(true).queue();

        GhostPingManager gp = GhostPingManager.getInstance();
        RandomMuteManager rm = RandomMuteManager.getInstance();
        StatusTrackerManager st = StatusTrackerManager.getInstance();
        CasinoManager casino = CasinoManager.getInstance();
        CaseManager cm = CaseManager.getInstance();

        long userId = event.getUser().getIdLong();
        long balance = casino.getBalance(userId);
        int caseCount = cm.getFullCaseInventory(userId).values().stream().mapToInt(Integer::intValue).sum();
        List<com.ragebait.cases.WeaponDrop> weapons = cm.getWeaponInventory(userId);
        long weaponValue = weapons.stream().mapToLong(com.ragebait.cases.WeaponDrop::getPrice).sum();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("\uD83C\uDFA3 Rage Bait Bot")
                .setDescription("Un bot pour faire rager tes potes!")
                .setColor(Color.ORANGE)
                .setThumbnail(event.getUser().getEffectiveAvatarUrl())
                .addField("\uD83D\uDCB0 Ton casino",
                        "Solde : **" + balance + "** \uD83E\uDE99\n" +
                        "Caisses : **" + caseCount + "**\n" +
                        "Armes : **" + weapons.size() + "** (valeur : **" + weaponValue + "** \uD83E\uDE99)", false)
                .addField("\uD83D\uDC7B Ghost Ping",
                        "/ghostping start/stop/status\n/settarget @user\n/addchannel #salon\n/setinterval <min> [sec]", false)
                .addField("\uD83D\uDD07 Random Mute",
                        "/randommute start @user [delai] [duree]\n/randommute stop/status", false)
                .addField("\uD83D\uDCDD Status Tracker",
                        "/statustrack start @user #salon\n/statustrack stop/status", false)
                .addField("\uD83C\uDFB0 Casino",
                        "/balance /daily /slots /coinflip /blackjack /give /leaderboard", false)
                .addField("\uD83C\uDF81 Caisses",
                        "/cases (shop) /inventory /opencase /weapons /sell", false)
                .addField("\uD83D\uDCCA Statuts",
                        "Ghost Ping: " + (gp.isRunning() ? "\uD83D\uDFE2" : "\uD83D\uDD34") +
                        " | Random Mute: " + (rm.isRunning() ? "\uD83D\uDFE2" : "\uD83D\uDD34") +
                        " | Status Tracker: " + (st.isEnabled() ? "\uD83D\uDFE2" : "\uD83D\uDD34"), false)
                .setFooter("Made with JDA ❤️");

        event.getHook().sendMessageEmbeds(embed.build()).queue();
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
        // deferReply car appel BDD potentiellement lent
        event.deferReply().queue();
        CasinoManager casino = CasinoManager.getInstance();
        var userOption = event.getOption("user");
        User target = userOption != null ? userOption.getAsUser() : event.getUser();
        
        long balance = casino.getBalance(target.getIdLong());
        
        if (target.equals(event.getUser())) {
            event.getHook().sendMessage("💰 Tu as **" + balance + "** 🪙").queue();
        } else {
            event.getHook().sendMessage("💰 " + target.getAsMention() + " a **" + balance + "** 🪙").queue();
        }
    }
    
    private void handleDaily(SlashCommandInteractionEvent event) {
        // deferReply car appel BDD potentiellement lent
        event.deferReply().queue();
        CasinoManager casino = CasinoManager.getInstance();
        var result = casino.claimDaily(event.getUser().getIdLong());
        
        if (result.success()) {
            long newBalance = casino.getBalance(event.getUser().getIdLong());
            event.getHook().sendMessage("🎁 Tu as récupéré **" + result.amount() + "** 🪙!\n💰 Nouveau solde: **" + newBalance + "** 🪙").queue();
        } else {
            long hours = result.cooldownRemaining() / 3600;
            long minutes = (result.cooldownRemaining() % 3600) / 60;
            event.getHook().sendMessage("⏰ Tu dois attendre encore **" + hours + "h " + minutes + "m** avant ton prochain daily!").setEphemeral(true).queue();
        }
    }
    
    private void handleSlots(SlashCommandInteractionEvent event) {
        // replyModal acquitte immédiatement l'interaction — pas de defer nécessaire ici
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
        // replyModal acquitte immédiatement l'interaction — pas de defer nécessaire ici
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
        
        // replyModal acquitte immédiatement l'interaction — pas de defer nécessaire ici
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
        // deferReply immédiatement pour éviter le timeout de 3s pendant les appels BDD
        event.deferReply().queue();

        CasinoManager casino = CasinoManager.getInstance();
        long userId = event.getUser().getIdLong();
        
        String betStr = event.getValue("bet").getAsString().trim();
        long bet;
        try {
            bet = Long.parseLong(betStr);
        } catch (NumberFormatException e) {
            event.getHook().sendMessage("❌ Mise invalide! Entre un nombre.").setEphemeral(true).queue();
            return;
        }
        
        var result = casino.playSlots(userId, bet);
        
        if (!result.played()) {
            event.getHook().sendMessage(result.message()).setEphemeral(true).queue();
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
        
        event.getHook().sendMessage(sb.toString())
            .addActionRow(
                Button.success("slots_replay", "🔄 Rejouer (même mise)"),
                Button.primary("slots_new", "🎰 Nouvelle mise")
            ).queue();
    }
    
    private void handleCoinflipModal(ModalInteractionEvent event) {
        // deferReply immédiatement pour éviter le timeout de 3s pendant les appels BDD
        event.deferReply().queue();

        CasinoManager casino = CasinoManager.getInstance();
        long userId = event.getUser().getIdLong();
        
        String betStr = event.getValue("bet").getAsString().trim();
        String choice = event.getValue("choice").getAsString().trim().toLowerCase();
        
        long bet;
        try {
            bet = Long.parseLong(betStr);
        } catch (NumberFormatException e) {
            event.getHook().sendMessage("❌ Mise invalide! Entre un nombre.").setEphemeral(true).queue();
            return;
        }
        
        boolean chooseHeads;
        if (choice.equals("pile") || choice.equals("p")) {
            chooseHeads = true;
        } else if (choice.equals("face") || choice.equals("f")) {
            chooseHeads = false;
        } else {
            event.getHook().sendMessage("❌ Choix invalide! Utilise 'pile' ou 'face'").setEphemeral(true).queue();
            return;
        }
        
        var result = casino.playCoinflip(userId, bet, chooseHeads);
        
        if (!result.played()) {
            event.getHook().sendMessage(result.message()).setEphemeral(true).queue();
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
        
        event.getHook().sendMessage(sb.toString())
            .addActionRow(
                Button.success("coinflip_replay_" + (chooseHeads ? "pile" : "face"), "🔄 Rejouer (même mise)"),
                Button.primary("coinflip_new", "🎲 Nouvelle mise")
            ).queue();
    }
    
    private void handleBlackjackModal(ModalInteractionEvent event) {
        // deferReply immédiatement pour éviter le timeout de 3s pendant les appels BDD
        event.deferReply().queue();

        CasinoManager casino = CasinoManager.getInstance();
        long userId = event.getUser().getIdLong();
        
        String betStr = event.getValue("bet").getAsString().trim();
        long bet;
        try {
            bet = Long.parseLong(betStr);
        } catch (NumberFormatException e) {
            event.getHook().sendMessage("❌ Mise invalide! Entre un nombre.").setEphemeral(true).queue();
            return;
        }
        
        if (bet <= 0) {
            event.getHook().sendMessage("❌ La mise doit être positive!").setEphemeral(true).queue();
            return;
        }
        
        if (casino.getBalance(userId) < bet) {
            event.getHook().sendMessage("❌ Tu n'as pas assez de 🪙! (Solde: " + casino.getBalance(userId) + ")").setEphemeral(true).queue();
            return;
        }
        
        if (activeGames.containsKey(userId)) {
            event.getHook().sendMessage("❌ Tu as déjà une partie en cours!").setEphemeral(true).queue();
            return;
        }
        
        var game = casino.startBlackjack(userId, bet);
        if (game == null) {
            event.getHook().sendMessage("❌ Impossible de démarrer la partie!").setEphemeral(true).queue();
            return;
        }
        
        activeGames.put(userId, game);
        lastBets.put(userId, bet);
        
        if (game.isGameOver()) {
            casino.finishBlackjack(game, game.getWinnings());
            activeGames.remove(userId);
            event.getHook().sendMessage(buildBlackjackMessage(game, true))
                .addActionRow(
                    Button.success("blackjack_replay", "🔄 Rejouer (même mise)"),
                    Button.primary("blackjack_new", "🃏 Nouvelle mise")
                ).queue();
        } else {
            event.getHook().sendMessage(buildBlackjackMessage(game, false))
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
            // deferEdit immédiatement pour éviter le timeout de 3s
            event.deferEdit().queue();

            var game = activeGames.get(userId);
            if (game == null) {
                event.getHook().sendMessage("❌ Tu n'as pas de partie en cours!").setEphemeral(true).queue();
                return;
            }
            
            if (buttonId.equals("bj_hit")) {
                game.hit();
                if (game.isGameOver()) {
                    casino.finishBlackjack(game, game.getWinnings());
                    activeGames.remove(userId);
                    event.getHook().editOriginal(buildBlackjackMessage(game, true))
                        .setActionRow(
                            Button.success("blackjack_replay", "🔄 Rejouer (même mise)"),
                            Button.primary("blackjack_new", "🃏 Nouvelle mise")
                        ).queue();
                } else {
                    event.getHook().editOriginal(buildBlackjackMessage(game, false)).queue();
                }
            } else {
                game.stand();
                casino.finishBlackjack(game, game.getWinnings());
                activeGames.remove(userId);
                event.getHook().editOriginal(buildBlackjackMessage(game, true))
                    .setActionRow(
                        Button.success("blackjack_replay", "🔄 Rejouer (même mise)"),
                        Button.primary("blackjack_new", "🃏 Nouvelle mise")
                    ).queue();
            }
            return;
        }
        
        // Slots replay
        if (buttonId.equals("slots_replay")) {
            // deferEdit immédiatement pour éviter le timeout de 3s
            event.deferEdit().queue();

            Long lastBet = lastBets.get(userId);
            if (lastBet == null) {
                event.getHook().sendMessage("❌ Aucune mise précédente trouvée!").setEphemeral(true).queue();
                return;
            }
            
            var result = casino.playSlots(userId, lastBet);
            if (!result.played()) {
                event.getHook().sendMessage(result.message()).setEphemeral(true).queue();
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
            
            event.getHook().editOriginal(sb.toString()).queue();
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
            
            // replyModal acquitte l'interaction directement
            event.replyModal(modal).queue();
            return;
        }
        
        // Coinflip replay
        if (buttonId.startsWith("coinflip_replay_")) {
            // deferEdit immédiatement pour éviter le timeout de 3s
            event.deferEdit().queue();

            Long lastBet = lastBets.get(userId);
            if (lastBet == null) {
                event.getHook().sendMessage("❌ Aucune mise précédente trouvée!").setEphemeral(true).queue();
                return;
            }
            
            boolean chooseHeads = buttonId.endsWith("pile");
            var result = casino.playCoinflip(userId, lastBet, chooseHeads);
            
            if (!result.played()) {
                event.getHook().sendMessage(result.message()).setEphemeral(true).queue();
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
            
            event.getHook().editOriginal(sb.toString()).queue();
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
            
            // replyModal acquitte l'interaction directement
            event.replyModal(modal).queue();
            return;
        }
        
        // Blackjack replay
        if (buttonId.equals("blackjack_replay")) {
            // deferEdit immédiatement pour éviter le timeout de 3s
            event.deferEdit().queue();

            Long lastBet = lastBets.get(userId);
            if (lastBet == null) {
                event.getHook().sendMessage("❌ Aucune mise précédente trouvée!").setEphemeral(true).queue();
                return;
            }
            
            if (activeGames.containsKey(userId)) {
                event.getHook().sendMessage("❌ Tu as déjà une partie en cours!").setEphemeral(true).queue();
                return;
            }
            
            if (casino.getBalance(userId) < lastBet) {
                event.getHook().sendMessage("❌ Tu n'as pas assez de 🪙! (Solde: " + casino.getBalance(userId) + ")").setEphemeral(true).queue();
                return;
            }
            
            var game = casino.startBlackjack(userId, lastBet);
            if (game == null) {
                event.getHook().sendMessage("❌ Impossible de démarrer la partie!").setEphemeral(true).queue();
                return;
            }
            
            activeGames.put(userId, game);
            
            if (game.isGameOver()) {
                casino.finishBlackjack(game, game.getWinnings());
                activeGames.remove(userId);
                event.getHook().editOriginal(buildBlackjackMessage(game, true))
                    .setActionRow(
                        Button.success("blackjack_replay", "🔄 Rejouer (même mise)"),
                        Button.primary("blackjack_new", "🃏 Nouvelle mise")
                    ).queue();
            } else {
                event.getHook().editOriginal(buildBlackjackMessage(game, false))
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
            
            // replyModal acquitte l'interaction directement
            event.replyModal(modal).queue();
        }
    }
    
    private void handleGive(SlashCommandInteractionEvent event) {
        // deferReply car appel BDD potentiellement lent
        event.deferReply().queue();
        CasinoManager casino = CasinoManager.getInstance();
        User target = event.getOption("user").getAsUser();
        long amount = event.getOption("montant").getAsLong();
        
        if (target.isBot()) {
            event.getHook().sendMessage("❌ Tu ne peux pas donner de 🪙 à un bot!").setEphemeral(true).queue();
            return;
        }
        
        if (target.getIdLong() == event.getUser().getIdLong()) {
            event.getHook().sendMessage("❌ Tu ne peux pas te donner de 🪙 à toi-même!").setEphemeral(true).queue();
            return;
        }
        
        if (amount <= 0) {
            event.getHook().sendMessage("❌ Le montant doit être positif!").setEphemeral(true).queue();
            return;
        }
        
        if (casino.transfer(event.getUser().getIdLong(), target.getIdLong(), amount)) {
            event.getHook().sendMessage("✅ Tu as donné **" + amount + "** 🪙 à " + target.getAsMention() + "\n💰 Ton solde: **" + casino.getBalance(event.getUser().getIdLong()) + "** 🪙").queue();
        } else {
            event.getHook().sendMessage("❌ Tu n'as pas assez de 🪙!").setEphemeral(true).queue();
        }
    }
    
    private void handleLeaderboard(SlashCommandInteractionEvent event) {
        // deferReply car appel BDD potentiellement lent
        event.deferReply().queue();
        CasinoManager casino = CasinoManager.getInstance();
        var leaderboard = casino.getLeaderboard(10);
        
        if (leaderboard.isEmpty()) {
            event.getHook().sendMessage("📊 Aucun joueur n'a encore de 🪙!").queue();
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
            
            // Récupérer le nom sans ping
            String userName = "Utilisateur inconnu";
            User user = event.getJDA().getUserById(entry.getKey());
            if (user != null) {
                userName = user.getEffectiveName();
            } else {
                // Essayer de récupérer depuis le cache du serveur
                var member = event.getGuild().getMemberById(entry.getKey());
                if (member != null) {
                    userName = member.getEffectiveName();
                }
            }
            
            sb.append(medal).append(" ").append(userName).append(" - **").append(entry.getValue()).append("** 🪙\n");
            rank++;
        }
        
        event.getHook().sendMessage(sb.toString()).queue();
    }
    
    // ============ ROULETTE COMMANDS ============
    
    private void handleRoulette(SlashCommandInteractionEvent event) {
        String action = event.getOption("action").getAsString().toLowerCase();
        RouletteManager roulette = RouletteManager.getInstance();
        
        switch (action) {
            case "start" -> {
                var channelOption = event.getOption("channel");
                TextChannel channel;
                
                if (channelOption != null) {
                    channel = channelOption.getAsChannel().asTextChannel();
                } else {
                    channel = event.getChannel().asTextChannel();
                }
                
                roulette.setJda(event.getJDA());
                roulette.setChannel(channel.getIdLong());
                roulette.start();
                
                event.reply("🎰 Roulette démarrée dans " + channel.getAsMention() + "!\n" +
                           "Délai: **" + roulette.getDelay() + " secondes** entre chaque tour").setEphemeral(true).queue();
            }
            case "stop" -> {
                roulette.stop();
                event.reply("🛑 Roulette arrêtée!").setEphemeral(true).queue();
            }
            case "status" -> {
                StringBuilder sb = new StringBuilder();
                sb.append("**🎰 Statut Roulette**\n\n");
                sb.append("**État:** ").append(roulette.isRunning() ? "🟢 Active" : "🔴 Inactive").append("\n");
                sb.append("**Délai:** ").append(roulette.getDelay()).append(" secondes\n");
                if (roulette.getChannelId() != null) {
                    sb.append("**Salon:** <#").append(roulette.getChannelId()).append(">");
                }
                event.reply(sb.toString()).setEphemeral(true).queue();
            }
            case "delay" -> {
                var delayOption = event.getOption("secondes");
                if (delayOption == null) {
                    event.reply("❌ Spécifie un délai! `/roulette delay secondes:30`").setEphemeral(true).queue();
                    return;
                }
                
                int delay = delayOption.getAsInt();
                if (delay < 10) {
                    event.reply("❌ Le délai minimum est de 10 secondes!").setEphemeral(true).queue();
                    return;
                }
                
                roulette.setDelay(delay);
                event.reply("✅ Délai changé à **" + delay + " secondes**").setEphemeral(true).queue();
            }
            default -> event.reply("❌ Action invalide! Utilise: start, stop, status, delay").setEphemeral(true).queue();
        }
    }
    
    private void handleBet(SlashCommandInteractionEvent event) {
        // deferReply car appel BDD potentiellement lent
        event.deferReply().setEphemeral(true).queue();
        RouletteManager roulette = RouletteManager.getInstance();
        
        if (!roulette.isRunning()) {
            event.getHook().sendMessage("❌ La roulette n'est pas active! Demande à un admin de la démarrer avec `/roulette start`").setEphemeral(true).queue();
            return;
        }
        
        long amount = event.getOption("montant").getAsLong();
        String betType = event.getOption("type").getAsString();
        
        String result = roulette.placeBet(event.getUser().getIdLong(), betType, amount);
        event.getHook().sendMessage(result).setEphemeral(true).queue();
    }
    
    private void handleMyBets(SlashCommandInteractionEvent event) {
        RouletteManager roulette = RouletteManager.getInstance();
        
        if (!roulette.isRunning()) {
            event.reply("❌ La roulette n'est pas active!").setEphemeral(true).queue();
            return;
        }
        
        String bets = roulette.getCurrentBets(event.getUser().getIdLong());
        event.reply(bets).setEphemeral(true).queue();
    }

    // ============ CASES COMMANDS ============

    /** /cases - shop interactif avec boutons Buy */
    private void handleCases(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        caseHandler.handleShop(event.getHook(), event.getUser().getIdLong());
    }

    /** /buycase <casename> - achat de caisse (legacy, garder pour compat) */
    private void handleBuyCase(SlashCommandInteractionEvent event) {
        // Redirige vers le shop interactif
        event.reply("Utilise `/cases` pour acheter des caisses via le shop interactif!").setEphemeral(true).queue();
    }

    /** /opencase <casename> - ouvrir une caisse avec animation */
    private void handleOpenCase(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        String caseName = event.getOption("casename").getAsString().trim().toLowerCase();
        caseHandler.handleOpenCase(
                event.getHook(),
                event.getUser().getIdLong(),
                event.getUser().getAsMention(),
                caseName
        );
    }

    /** /inventory [user] - inventaire unifie caisses + armes avec boutons */
    private void handleInventory(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        User target = event.getOption("user") != null
                ? event.getOption("user").getAsUser()
                : event.getUser();
        caseHandler.handleInventory(event.getHook(), event.getUser().getIdLong(), target);
    }

    /** /weapons - inventaire detaille des armes */
    private void handleWeapons(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        caseHandler.handleWeapons(
                event.getHook(),
                event.getUser().getIdLong(),
                event.getUser().getName()
        );
    }

    /** /sell id:<n> - vendre une arme individuelle */
    private void handleSell(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        int weaponId = event.getOption("id").getAsInt();
        caseHandler.handleSell(
                event.getHook(),
                event.getUser().getIdLong(),
                weaponId
        );
    }
}

