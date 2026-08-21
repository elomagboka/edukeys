# syntax=docker/dockerfile:1

# Le contexte de build est la RACINE du dépôt, pas backend/ : le module
# backend hérite de edukeys-parent (pom.xml racine), que Maven doit pouvoir
# lire pour résoudre la version de Spring Boot et les propriétés communes.
# Construire depuis backend/ seul échoue sur « Non-resolvable parent POM ».

# ---------------------------------------------------------------- build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Les dépendances sont téléchargées dans une couche séparée : tant qu'aucun
# pom.xml ne change, Docker réutilise le cache et le build reste rapide.
# -am construit aussi les modules dont backend dépend (ici le parent).
COPY pom.xml .
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -pl backend -am dependency:go-offline

COPY backend/src backend/src
RUN mvn -B -pl backend -am -DskipTests package

# ------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Flyway CLI : utilisé par la commande de pré-déploiement de Render, qui
# s'exécute dans cette image avant que la nouvelle version prenne le service.
ARG FLYWAY_VERSION=11.7.2
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && curl -fsSL "https://repo1.maven.org/maven2/org/flywaydb/flyway-commandline/${FLYWAY_VERSION}/flyway-commandline-${FLYWAY_VERSION}-linux-x64.tar.gz" \
    | tar xz -C /opt \
 && ln -s "/opt/flyway-${FLYWAY_VERSION}/flyway" /usr/local/bin/flyway \
 && apt-get purge -y curl \
 && apt-get autoremove -y \
 && rm -rf /var/lib/apt/lists/*

# Les migrations sont copiées hors du jar : la commande de pré-déploiement y
# accède directement, sans démarrer l'application. Seul db/migration est
# copié — db/testdata ne doit jamais atteindre un environnement déployé.
COPY --from=build /build/backend/src/main/resources/db/migration /app/migrations
COPY --from=build /build/backend/target/edukeys-backend.jar /app/app.jar

# Exécution sans privilèges
RUN useradd --system --uid 1001 edukeys && chown -R edukeys:edukeys /app
USER edukeys

EXPOSE 8080

# Render fournit la mémoire disponible au conteneur ; on laisse la JVM s'y
# adapter plutôt que de figer un -Xmx qui deviendrait faux au changement de plan.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
