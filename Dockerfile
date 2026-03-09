# Étape 1 : build Maven
FROM maven:3.9.3-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Étape 2 : runtime léger
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copier le JAR généré depuis l'étape build
COPY --from=build /app/target/discord-rage-bait-1.0-SNAPSHOT.jar bot.jar

# Variables d'environnement (optionnel, on peut aussi passer via .env)
ENV DISCORD_TOKEN=""
ENV DB_HOST=""
ENV DB_PORT="5432"
ENV DB_NAME=""
ENV DB_USER=""
ENV DB_PASSWORD=""

# Lancer le bot
CMD ["java", "-jar", "bot.jar"]