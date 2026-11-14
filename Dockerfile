# ── Stage 1 : Build avec Maven ──────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copier pom.xml en premier pour profiter du cache des dépendances
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copier le code source et compiler
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2 : Image de production légère ────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Créer le répertoire d'uploads
RUN mkdir -p /opt/render/project/uploads

# Copier le JAR buildé
COPY --from=build /app/target/backend-civica-0.0.1-SNAPSHOT.jar app.jar

# Port exposé par Spring Boot
EXPOSE 8080

# Démarrage avec le profil prod
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
