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
    exit
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

BOT_NAME="ragebait-bot"
IMAGE_NAME="ragebait-bot"
BOT_DIR="$REPO_DIR"
ENV_FILE="$ENV_FILE"

case "\$1" in

start)
docker run -d --name \$BOT_NAME --env-file \$ENV_FILE --restart unless-stopped \$IMAGE_NAME
;;

stop)
docker stop \$BOT_NAME 2>/dev/null
docker rm \$BOT_NAME 2>/dev/null
;;

logs)
docker logs -f \$BOT_NAME
;;

update)
docker stop \$BOT_NAME 2>/dev/null
docker rm \$BOT_NAME 2>/dev/null
cd \$BOT_DIR
git pull
docker build -t \$IMAGE_NAME .
docker run -d --rm --name \$BOT_NAME --env-file \$ENV_FILE \$IMAGE_NAME
;;

*)
echo "Usage: bot {start|stop|logs|update}"
;;

esac
EOF

sudo chmod +x $BOT_CMD

echo "Build Docker..."
docker build -t ragebait-bot .

echo "Installation terminée."
echo ""
echo "Commandes disponibles :"
echo "bot start"
echo "bot stop"
echo "bot logs"
echo "bot update"