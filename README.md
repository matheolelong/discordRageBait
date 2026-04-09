# Discord Rage Bait Bot 🎣👻

Un bot Discord en Java utilisant JDA (Java Discord API) pour faire rager tes potes!

## Fonctionnalités

- **Ghost Ping** - Envoie des pings fantômes qui sont supprimés instantanément
- **Random Mute** - Mute/deafen aléatoirement une personne en vocal
- **Status Tracker** - Surveille et enregistre les changements de statut personnalisé d'un utilisateur
- **Casino** - Économie virtuelle avec slots, coinflip, blackjack et roulette
- **Caisses CS:GO** - Ouvre des caisses pour obtenir des armes avec float et qualité

## Prérequis

- Java 17 ou supérieur
- PostgreSQL
- Un compte Discord Developer et un token de bot

## Configuration

### 1. Créer une application Discord

1. Va sur le [Discord Developer Portal](https://discord.com/developers/applications)
2. Clique sur "New Application" et donne un nom à ton bot
3. Va dans l'onglet "Bot" et clique sur "Add Bot"
4. Copie le token du bot (garde-le secret!)
5. Active les intents dans "Privileged Gateway Intents":
   - ✅ **MESSAGE CONTENT INTENT**
   - ✅ **PRESENCE INTENT** (pour le status tracker)
   - ✅ **SERVER MEMBERS INTENT** (pour le status tracker)

### 2. Inviter le bot sur ton serveur

1. Va dans l'onglet "OAuth2" > "URL Generator"
2. Sélectionne les scopes: `bot`, `applications.commands`
3. Sélectionne les permissions: `Send Messages`, `Read Message History`, `Use Slash Commands`, `Manage Messages`, `Mute Members`, `Deafen Members`
4. Copie l'URL générée et ouvre-la dans ton navigateur pour inviter le bot

## Lancer le bot

### Option 1 : Variable d'environnement
```powershell
$env:DISCORD_TOKEN = "ton_token_ici"
.\mvnw.cmd clean package -DskipTests
java -jar target\discord-rage-bait-1.0-SNAPSHOT.jar
```

### Option 2 : Docker Compose
```yaml
# docker-compose.yml
services:
  bot:
    build: .
    environment:
      DISCORD_TOKEN: ton_token
      DB_HOST: db
      DB_PORT: 5432
      DB_NAME: ragebait
      DB_USER: postgres
      DB_PASSWORD: secret
    volumes:
      - ./cases:/app/cases   # dossier des caisses
```

## Commandes disponibles

### 🎁 Caisses (CS:GO-like)
Système de caisses avec drops pondérés, float, qualité et prix dynamique.

| Commande | Description |
|----------|-------------|
| `/cases` | Liste toutes les caisses disponibles avec leur prix |
| `/buycase <nom>` | Achète une caisse (déduit les coins de ton solde) |
| `/opencase <nom>` | Ouvre une caisse de ton inventaire avec animation |
| `/inventory` | Voir les caisses que tu possèdes |

**Qualités (selon le float) :**
| Float | Qualité |
|-------|---------|
| 0.00 – 0.07 | ✨ Factory New |
| 0.07 – 0.15 | 🟢 Minimal Wear |
| 0.15 – 0.38 | 🔵 Field-Tested |
| 0.38 – 0.45 | 🟡 Well-Worn |
| 0.45 – 1.00 | 🔴 Battle-Scarred |

**Configuration des caisses :** édite `cases/cases.json` (créé automatiquement au premier démarrage).
Un float proche de `floatMin` → prix proche de `maxPrice`, et inversement.

---

### Ghost Ping 👻
| Commande | Description |
|----------|-------------|
| `/ghostping start` | Démarre l'envoi automatique de ghost pings |
| `/ghostping stop` | Arrête le ghost ping |
| `/ghostping status` | Affiche le statut actuel |
| `/addchannel #salon` | Ajoute un salon pour les ghost pings |
| `/removechannel #salon` | Retire un salon |
| `/setinterval <min> [sec]` | Change l'intervalle (ex: `/setinterval 0 30` = 30s) |
| `/settarget @user` | Définit un utilisateur à mentionner |

### Random Mute 🔇
| Commande | Description |
|----------|-------------|
| `/randommute start @user` | Démarre le mute aléatoire sur la cible |
| `/randommute start @user delai:60` | Délai max de 60 secondes entre chaque mute |
| `/randommute start @user duree:1000` | Durée du mute de 1000ms |
| `/randommute stop` | Arrête le random mute |
| `/randommute status` | Affiche le statut |

Le bot choisit aléatoirement entre mute vocal et mute casque (deafen), avec un délai aléatoire entre 1 seconde et le délai max défini.

### Status Tracker 📝
| Commande | Description |
|----------|-------------|
| `/statustrack start @user #salon` | Surveille le statut personnalisé de l'utilisateur |
| `/statustrack stop` | Arrête la surveillance |
| `/statustrack status` | Affiche le statut |

Quand la cible change son statut personnalisé Discord, un message est envoyé dans le salon défini :
> 📝 **Nouvelle citation de [nom] :**
> > Le statut de la personne

### Qui t'a demandé 🤫
Fonctionnalité passive : quand quelqu'un écrit "qui" dans un message, le bot répond automatiquement "Qui t'a demandé".

| Commande | Description |
|----------|-------------|
| `/quiexclude add @user` | Exclure un utilisateur (le bot ne lui répondra plus) |
| `/quiexclude remove @user` | Retirer l'exclusion |
| `/quiexclude list` | Voir la liste des utilisateurs exclus |

### Casino 🎰
Système d'économie virtuelle avec plusieurs jeux!

| Commande | Description |
|----------|-------------|
| `/balance [user]` | Voir ton solde ou celui d'un autre |
| `/daily` | Récupère 500 🪙 toutes les 24h |
| `/slots <mise>` | Machine à sous (x2 à x15 selon symboles) |
| `/coinflip <mise> <pile/face>` | Pile ou Face (x2) |
| `/blackjack <mise>` | Blackjack interactif avec boutons |
| `/give @user <montant>` | Donner des 🪙 à quelqu'un |
| `/leaderboard` | Top 10 des plus riches |

**Multiplicateurs Slots:**
- 🍒🍒🍒 = x2 | 🍋🍋🍋 = x2.5 | 🍊🍊🍊 = x3
- 🍇🍇🍇 = x4 | 7️⃣7️⃣7️⃣ = x7 | 💎💎💎 = x10 | 🎰🎰🎰 = x15
- 2 symboles identiques = x1.5

### Roulette 🎡
| Commande | Description |
|----------|-------------|
| `/roulette start [#salon]` | Démarre la roulette dans un salon |
| `/roulette stop` | Arrête la roulette |
| `/roulette status` | Affiche le statut |
| `/roulette delay secondes:<n>` | Change le délai entre les tours (min 10s) |
| `/bet <montant> <type>` | Place un pari (rouge/noir/pair/impair/0-36) |
| `/mybets` | Voir tes paris en cours |

### Autres
| Commande | Description |
|----------|-------------|
| `/ping` | Teste la latence du bot |
| `/info` | Affiche les informations du bot |
| `/ragebait [message]` | Envoie un message provocateur aléatoire |

## Fonctionnement du Ghost Ping

Le bot envoie un message (ou un ping si une cible est définie) dans un salon aléatoire parmi ceux configurés, puis le supprime après 1 seconde. Cela crée une notification fantôme!

## Sauvegarde automatique

La configuration ghost ping (salons, cible, intervalle) est automatiquement sauvegardée dans la base de données et rechargée au redémarrage du bot.

## Structure du projet

```
src/main/java/com/ragebait/
├── Main.java                    # Point d'entrée du bot
├── CasinoManager.java           # Gère l'économie virtuelle
├── GhostPingManager.java        # Gère les ghost pings automatiques
├── RandomMuteManager.java       # Gère les mutes aléatoires
├── RouletteManager.java         # Gère la roulette
├── StatusTrackerManager.java    # Gère la surveillance des statuts
├── ConfigManager.java           # Sauvegarde/chargement de la config
├── DatabaseManager.java         # Connexion PostgreSQL
├── cases/
│   ├── Case.java                # Modèle d'une caisse
│   ├── Weapon.java              # Modèle d'une arme
│   ├── CaseLoader.java          # Parse cases/cases.json
│   ├── CaseManager.java         # Singleton : chargement + inventaire BDD
│   ├── CaseCommandHandler.java  # Logique des commandes /buycase /opencase
│   ├── DropSystem.java          # Tirage pondéré par dropChance
│   └── FloatCalculator.java     # Calcul float, qualité et prix
└── listeners/
    ├── MessageListener.java     # Gère les messages textuels
    ├── SlashCommandListener.java # Gère les commandes slash
    └── PresenceListener.java    # Gère les événements de présence

cases/
└── cases.json                   # Config des caisses (créé automatiquement)
```

## Technologies utilisées

- [JDA 5](https://github.com/discord-jda/JDA) - Java Discord API
- [PostgreSQL](https://www.postgresql.org/) - Base de données
- [Maven Wrapper](https://maven.apache.org/wrapper/) - Pas besoin d'installer Maven

## License

MIT
