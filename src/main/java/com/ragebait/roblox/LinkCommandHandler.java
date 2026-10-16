package com.ragebait.roblox;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/**
 * /link <code> : lie le compte Discord au compte Roblox (code affiche dans le jeu).
 */
public class LinkCommandHandler {

    private final RobloxLinkManager linkManager = RobloxLinkManager.getInstance();

    public void handle(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption("code");
        if (option == null) {
            event.reply("❌ Donne le code affiché dans le jeu : `/link CODE`").setEphemeral(true).queue();
            return;
        }

        event.deferReply().setEphemeral(true).queue();
        RobloxLinkManager.LinkResult result = linkManager.link(event.getUser().getIdLong(), option.getAsString());

        String message = switch (result.status()) {
            case LINKED -> "✅ Compte lié au joueur Roblox `" + result.robloxId()
                    + "` ! Ton solde 🪙 est maintenant partagé avec le jeu.";
            case INVALID_CODE -> "❌ Code invalide ou expiré. Génère un nouveau code dans le jeu.";
            case ERROR -> "❌ Erreur pendant la liaison, réessaie plus tard.";
        };
        event.getHook().editOriginal(message).queue();
    }
}
