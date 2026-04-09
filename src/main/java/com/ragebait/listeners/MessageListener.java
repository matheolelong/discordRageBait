package com.ragebait.listeners;

import com.ragebait.QuiExclusionManager;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.regex.Pattern;

public class MessageListener extends ListenerAdapter {

    // Pattern pour detecter "qui" comme mot isole (pas dans "quiche", "equipe", etc.)
    private static final Pattern QUI_PATTERN = Pattern.compile("\\bqui\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignorer les messages du bot lui-meme
        if (event.getAuthor().isBot()) {
            return;
        }

        String message = event.getMessage().getContentRaw();
        String messageLower = message.toLowerCase();

        // Repondre "Qui t'a demande" quand quelqu'un dit "qui" (sauf exclus)
        if (QUI_PATTERN.matcher(message).find()) {
            if (!QuiExclusionManager.getInstance().isExcluded(event.getAuthor().getIdLong())) {
                event.getChannel().sendMessage("Qui t'a demandé").queue();
            }
            return;
        }

        // Exemple: repondre a certains mots-cles
        if (messageLower.contains("bonjour") || messageLower.contains("salut")) {
            event.getChannel().sendMessage("Salut " + event.getAuthor().getAsMention() + "! 👋").queue();
        }

        // Commande prefixe simple (alternative aux slash commands)
        if (messageLower.startsWith("!rage")) {
            String[] rageBaitMessages = {
                    "Je pense que les pizzas a l'ananas sont les meilleures 🍕🍍",
                    "Star Wars episode 8 est le meilleur de la saga 🎬",
                    "Le lait avant les cereales, c'est la seule vraie facon 🥛",
                    "Les chats sont clairement superieurs aux chiens 🐱",
                    "L'eau chaude est meilleure que l'eau froide 💧"
            };

            int randomIndex = (int) (Math.random() * rageBaitMessages.length);
            event.getChannel().sendMessage(rageBaitMessages[randomIndex]).queue();
        }
    }
}
