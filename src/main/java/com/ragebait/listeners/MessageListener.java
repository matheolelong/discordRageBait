package com.ragebait.listeners;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class MessageListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignorer les messages du bot lui-même
        if (event.getAuthor().isBot()) {
            return;
        }

        String message = event.getMessage().getContentRaw().toLowerCase();

        // Exemple: répondre à certains mots-clés
        if (message.contains("bonjour") || message.contains("salut")) {
            event.getChannel().sendMessage("Salut " + event.getAuthor().getAsMention() + "! 👋").queue();
        }

        // Commande préfixe simple (alternative aux slash commands)
        if (message.startsWith("!rage")) {
            String[] rageBaitMessages = {
                    "Je pense que les pizzas à l'ananas sont les meilleures 🍕🍍",
                    "Star Wars épisode 8 est le meilleur de la saga 🎬",
                    "Le lait avant les céréales, c'est la seule vraie façon 🥛",
                    "Les chats sont clairement supérieurs aux chiens 🐱",
                    "L'eau chaude est meilleure que l'eau froide 💧"
            };
            
            int randomIndex = (int) (Math.random() * rageBaitMessages.length);
            event.getChannel().sendMessage(rageBaitMessages[randomIndex]).queue();
        }
    }
}
