#!/bin/bash

echo "🚀 Installation du bot RageBait..."

REPO_DIR=$(pwd)
ENV_FILE="$REPO_DIR/.env"
BOT_CMD="/usr/local/bin/bot"

# Vérifier Docker
if ! command -v docker &> /dev/null
then
    echo "❌ Docker n'est pas installé."
    echo "Installe Docker puis relance le script."
    exit 1
fi

# Création du .env si absent
if [ ! -f "$ENV_FILE" ]; then
    echo "📄 Création du fichier .env"
    if [ -f ".env.example" ]; then
        cp .env.example .env
    else
        touch .env
    fi
    echo "⚠️  Edite le fichier .env avec ton token Discord et tes accès."
fi

# Création du dossier cases s'il n'existe pas
mkdir -p "$REPO_DIR/cases"

# Création de la commande système 'bot'
echo "🛠️  Création de la commande : $BOT_CMD"

sudo tee $BOT_CMD > /dev/null <<EOF
#!/bin/bash

# Variables globales
BOT_NAME="ragebait-bot"
IMAGE_NAME="ragebait-bot"
BOT_DIR="$REPO_DIR"
ENV_FILE="\$BOT_DIR/.env"
CASES_FILE="\$BOT_DIR/cases/cases.json"

case "\$1" in

start)
    if docker ps --format '{{.Names}}' | grep -q "^\$BOT_NAME\$"; then
        echo "✅ Le bot tourne déjà."
        exit 0
    fi
    if [ ! -f "\$CASES_FILE" ]; then
        echo "❌ Erreur : \$CASES_FILE est introuvable."
        echo "Place ton fichier cases.json dans le dossier cases/ avant de démarrer."
        exit 1
    fi
    echo "Démarrage du bot..."
    docker run -d --name \$BOT_NAME \\
        --env-file \$ENV_FILE \\
        --restart unless-stopped \\
        -v "\$CASES_FILE:/app/cases/cases.json" \\
        \$IMAGE_NAME
    echo "🚀 Bot démarré avec succès."
;;

stop)
    echo "Arrêt du bot..."
    docker stop \$BOT_NAME 2>/dev/null || true
    docker rm \$BOT_NAME 2>/dev/null || true
    echo "🛑 Bot arrêté et supprimé."
;;

status)
    if docker ps --format '{{.Names}}' | grep -q "^\$BOT_NAME\$"; then
        echo "🟢 Statut : En ligne"
    else
        echo "🔴 Statut : Hors ligne"
    fi
;;

logs)
    docker logs -f \$BOT_NAME
;;

rebuild)
    echo "🔨 Rebuild de l'image..."
    cd "\$BOT_DIR"
    docker build -t \$IMAGE_NAME .
    \$0 stop
    \$0 start
    echo "✅ Rebuild terminé."
;;

update)
    echo "🔄 Mise à jour depuis Git..."
    cd "\$BOT_DIR"
    # Pull sans quitter le script en cas d'erreur (ex: pas de repo git)
    git pull || echo "⚠️  Attention : 'git pull' a échoué."
    
    echo "🔨 Rebuild après mise à jour..."
    docker build -t \$IMAGE_NAME .
    
    # On redémarre proprement
    \$0 stop
    \$0 start
    echo "✨ Mise à jour et redémarrage terminés."
;;

*)
    echo "Usage: bot {start|stop|status|logs|rebuild|update}"
;;

esac
EOF

# Rendre le script exécutable
sudo chmod +x $BOT_CMD

# Premier Build
echo "🏗️  Build initial de l'image Docker..."
docker build -t ragebait-bot .

echo ""
echo "✅ Installation terminée !"
echo "--------------------------------------"
echo "Commandes utilisables partout :"
echo "  bot start    -> Lancer le bot"
echo "  bot stop     -> Arrêter le bot"
echo "  bot status   -> Voir si le bot est en vie"
echo "  bot logs     -> Voir la console en direct"
echo "  bot update   -> Télécharger les modifs git + redémarrer"
echo "  bot rebuild  -> Re-compiler sans télécharger (utile si tu modifies cases.json)"
echo "--------------------------------------"