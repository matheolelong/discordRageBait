#!/bin/bash

echo "Installation du bot RageBait..."

REPO_DIR=$(pwd)
ENV_FILE="$REPO_DIR/.env"
BOT_CMD="/usr/local/bin/bot"

# Vérifier Docker
if ! command -v docker &> /dev/null
then
    echo "Docker n'est pas installé."
    echo "Installe Docker puis relance le script."
    exit 1
fi

# Création du .env si absent
if [ ! -f "$ENV_FILE" ]; then
    echo "Création du fichier .env"
    cp .env.example .env
    echo "⚠️  Edite le fichier .env avec ton token Discord et ta base de données"
fi

# Création commande bot
echo "Création de la commande bot..."

sudo tee $BOT_CMD > /dev/null <<EOF
#!/bin/bash

set -e

BOT_NAME="ragebait-bot"
IMAGE_NAME="ragebait-bot"
BOT_DIR="$REPO_DIR"
ENV_FILE="$ENV_FILE"
CASES_FILE="\$BOT_DIR/cases/cases.json"

case "\$1" in

start)
  if docker ps --format '{{.Names}}' | grep -q "^\$BOT_NAME\$"; then
    echo "Le bot tourne déjà."
    exit 0
  fi
  if [ ! -f "\$CASES_FILE" ]; then
    echo "❌ cases/cases.json introuvable, abandon."
    exit 1
  fi
  docker run -d --name \$BOT_NAME \\
    --env-file \$ENV_FILE \\
    --restart unless-stopped \\
    -v \$CASES_FILE:/app/cases/cases.json \\
    \$IMAGE_NAME
  echo "✅ Bot démarré."
;;

stop)
  docker stop \$BOT_NAME 2>/dev/null
  docker rm \$BOT_NAME 2>/dev/null
  echo "✅ Bot arrêté."
;;

status)
  if docker ps --format '{{.Names}}' | grep -q "^\$BOT_NAME\$"; then
    echo "✅ Le bot tourne."
  else
    echo "❌ Le bot est arrêté."
  fi
;;

logs)
  docker logs -f \$BOT_NAME
;;

rebuild)
  docker stop \$BOT_NAME 2>/dev/null
  docker rm \$BOT_NAME 2>/dev/null
  cd \$BOT_DIR
  docker build -t \$IMAGE_NAME .
  if [ ! -f "\$CASES_FILE" ]; then
    echo "❌ cases/cases.json introuvable, abandon."
    exit 1
  fi
  docker run -d --name \$BOT_NAME \\
    --env-file \$ENV_FILE \\
    --restart unless-stopped \\
    -v \$CASES_FILE:/app/cases/cases.json \\
    \$IMAGE_NAME
  echo "✅ Bot rebuild et redémarré."
;;

update)
  docker stop \$BOT_NAME 2>/dev/null
  docker rm \$BOT_NAME 2>/dev/null
  cd \$BOT_DIR
  git pull || { echo "❌ git pull a échoué, abandon."; exit 1; }
  docker build -t \$IMAGE_NAME .
  if [ ! -f "\$CASES_FILE" ]; then
    echo "❌ cases/cases.json introuvable, abandon."
    exit 1
  fi
  docker run -d --name \$BOT_NAME \\
    --env-file \$ENV_FILE \\
    --restart unless-stopped \\
    -v \$CASES_FILE:/app/cases/cases.json \\
    \$IMAGE_NAME
  echo "✅ Bot mis à jour et redémarré."
;;

*)
  echo "Usage: bot {start|stop|status|logs|rebuild|update}"
;;

esac
EOF

sudo chmod +x $BOT_CMD

echo "Build Docker..."
docker build -t ragebait-bot .

echo ""
echo "✅ Installation terminée."
echo ""
echo "Commandes disponibles :"
echo "  bot start    → Démarre le bot"
echo "  bot stop     → Arrête le bot"
echo "  bot status   → Vérifie si le bot tourne"
echo "  bot logs     → Affiche les logs en direct"
echo "  bot rebuild  → Rebuild l'image et redémarre (sans git pull)"
echo "  bot update   → git pull + rebuild + redémarre"