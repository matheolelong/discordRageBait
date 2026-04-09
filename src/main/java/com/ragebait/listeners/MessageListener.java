package com.ragebait.listeners;

import com.ragebait.QuiExclusionManager;
import com.ragebait.cases.CaseCommandHandler;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.regex.Pattern;

public class MessageListener extends ListenerAdapter {

    // Pattern pour détecter "qui" comme mot isolé (pas dans "quiche", "équipe", etc.)
    private static final Pattern QUI_PATTERN = Pattern.compile("\\bqui\\b", Pattern.CASE_INSENSITIVE);

    /** Handler pour les commandes !buycase, !opencase, !cases, !inventory */
    private final CaseCommandHandler caseHandler = new CaseCommandHandler();

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignorer les messages du bot lui-même
        if (event.getAuthor().isBot()) {
            return;
        }

        // Commandes de caisse (priorité haute : !buycase, !opencase, !cases, !inventory)
        if (caseHandler.handle(event)) {
            return;
        }

        String message = event.getMessage().getContentRaw();
        String messageLower = message.toLowerCase();

        // Répondre "Qui t'a demandé" quand quelqu'un dit "qui" (sauf exclus)
        if (QUI_PATTERN.matcher(message).find()) {
            if (!QuiExclusionManager.getInstance().isExcluded(event.getAuthor().getIdLong())) {
                event.getChannel().sendMessage("Qui t'a demandé").queue();
            }
            return;
        }

        // Exemple: répondre à certains mots-clés
        if (messageLower.contains("bonjour") || messageLower.contains("salut")) {
            event.getChannel().sendMessage("Salut " + event.getAuthor().getAsMention() + "! 👋").queue();
        }

        // Commande préfixe simple (alternative aux slash commands)
        if (messageLower.startsWith("!rage")) {
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
