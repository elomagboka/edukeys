# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Les dépendances sont téléchargées dans une couche séparée : tant que le
# pom.xml ne change pas, Docker réutilise le cache et le build reste rapide.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

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
# accède directement, sans démarrer l'application.
COPY --from=build /build/src/main/resources/db/migration /app/migrations
COPY --from=build /build/target/*.jar /app/app.jar

# Exécution sans privilèges
RUN useradd --system --uid 1001 edukeys && chown -R edukeys:edukeys /app
USER edukeys

EXPOSE 8080

# Render fournit la mémoire disponible au conteneur ; on laisse la JVM s'y
# adapter plutôt que de figer un -Xmx qui deviendrait faux au changement de plan.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
