# Discord Ghost Ping Bot 👻

Un bot Discord en Java utilisant JDA (Java Discord API) pour envoyer des ghost messages (messages envoyés puis supprimés instantanément).

## Prérequis

- Java 17 ou supérieur
- Un compte Discord Developer et un token de bot

## Configuration

### 1. Créer une application Discord

1. Va sur le [Discord Developer Portal](https://discord.com/developers/applications)
2. Clique sur "New Application" et donne un nom à ton bot
3. Va dans l'onglet "Bot" et clique sur "Add Bot"
4. Copie le token du bot (garde-le secret!)
5. Active les intents "MESSAGE CONTENT INTENT" dans la section "Privileged Gateway Intents"

### 2. Inviter le bot sur ton serveur

1. Va dans l'onglet "OAuth2" > "URL Generator"
2. Sélectionne les scopes: `bot`, `applications.commands`
3. Sélectionne les permissions: `Send Messages`, `Read Message History`, `Use Slash Commands`, `Manage Messages`
4. Copie l'URL générée et ouvre-la dans ton navigateur pour inviter le bot

## Lancer le bot

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.2"
$env:DISCORD_TOKEN = "ton_token_ici"
.\mvnw.cmd compile exec:java "-Dexec.mainClass=com.ragebait.Main"
```

## Commandes disponibles

### Ghost Ping
| Commande | Description |
|----------|-------------|
| `/ghostping start` | Démarre l'envoi automatique de ghost messages |
| `/ghostping stop` | Arrête le ghost ping |
| `/ghostping status` | Affiche le statut actuel |
| `/addchannel #salon` | Ajoute un salon pour les ghost messages |
| `/removechannel #salon` | Retire un salon |
| `/setinterval <minutes>` | Change l'intervalle (défaut: 5 min) |
| `/settarget @user` | Définit un utilisateur à mentionner (optionnel) |

### Autres
| Commande | Description |
|----------|-------------|
| `/ping` | Teste la latence du bot |
| `/info` | Affiche les informations du bot |

## Fonctionnement

Le bot envoie un message "." dans les salons configurés, puis le supprime après 1 seconde. Cela crée une notification fantôme - ton pote verra qu'il y a un nouveau message mais ne trouvera rien!

## Structure du projet

```
src/main/java/com/ragebait/
├── Main.java                    # Point d'entrée du bot
├── GhostPingManager.java        # Gère les ghost pings automatiques
└── listeners/
    ├── MessageListener.java     # Gère les messages textuels
    └── SlashCommandListener.java # Gère les commandes slash
```

## Technologies utilisées

- [JDA 5](https://github.com/discord-jda/JDA) - Java Discord API
- [Maven Wrapper](https://maven.apache.org/wrapper/) - Pas besoin d'installer Maven

## License

MIT
