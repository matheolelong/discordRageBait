package com.ragebait.listeners;

import com.ragebait.GhostPingManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.util.Set;

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
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("👻 Ghost Ping Bot")
                .setDescription("Un bot pour faire rager tes potes avec des ghost pings!")
                .setColor(Color.ORANGE)
                .addField("Commandes Ghost Ping", """
                        `/ghostping start` - Démarre le ghost ping
                        `/ghostping stop` - Arrête le ghost ping
                        `/ghostping status` - Affiche le statut
                        `/settarget @user` - Définit la cible
                        `/addchannel #salon` - Ajoute un salon
                        `/removechannel #salon` - Retire un salon
                        `/setinterval <min>` - Change l'intervalle
                        """, false)
                .addField("Statut", gp.isRunning() ? "🟢 Actif" : "🔴 Inactif", true)
                .addField("Intervalle", gp.getInterval() + " min", true)
                .addField("Salons", String.valueOf(gp.getChannelIds().size()), true)
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
                event.reply("👻 Ghost ping démarré! Intervalle: " + gp.getInterval() + " minutes").setEphemeral(true).queue();
            }
            case "stop" -> {
                gp.stop();
                event.reply("🛑 Ghost ping arrêté!").setEphemeral(true).queue();
            }
            case "status" -> {
                StringBuilder sb = new StringBuilder();
                sb.append("**Statut:** ").append(gp.isRunning() ? "🟢 Actif" : "🔴 Inactif").append("\n");
                sb.append("**Intervalle:** ").append(gp.getInterval()).append(" minutes\n");
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
        GhostPingManager.getInstance().setTargetUser(target.getIdLong());
        event.reply("🎯 Cible définie: " + target.getAsMention()).setEphemeral(true).queue();
    }

    private void handleAddChannel(SlashCommandInteractionEvent event) {
        var channel = event.getOption("channel").getAsChannel();
        if (channel instanceof TextChannel) {
            GhostPingManager.getInstance().addChannel(channel.getIdLong());
            event.reply("✅ Salon ajouté: <#" + channel.getIdLong() + ">").setEphemeral(true).queue();
        } else {
            event.reply("❌ Ce n'est pas un salon textuel!").setEphemeral(true).queue();
        }
    }

    private void handleRemoveChannel(SlashCommandInteractionEvent event) {
        var channel = event.getOption("channel").getAsChannel();
        GhostPingManager.getInstance().removeChannel(channel.getIdLong());
        event.reply("✅ Salon retiré: <#" + channel.getIdLong() + ">").setEphemeral(true).queue();
    }

    private void handleSetInterval(SlashCommandInteractionEvent event) {
        int minutes = event.getOption("minutes").getAsInt();
        if (minutes < 1) {
            event.reply("❌ L'intervalle doit être d'au moins 1 minute!").setEphemeral(true).queue();
            return;
        }
        GhostPingManager.getInstance().setInterval(minutes);
        event.reply("⏱️ Intervalle défini à " + minutes + " minute(s)").setEphemeral(true).queue();
    }
}
