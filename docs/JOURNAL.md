# Journal de développement

> Rempli automatiquement par la commande `/point` en fin de session.
> Se lit au démarrage de la session suivante : il remplace l'historique de conversation.

## 2026-08-16 — T-02
- Fait : package `common/` complet — `BaseEntity` (id UUID v7 via générateur personnalisé, `actif`, audit JPA), `EntiteDesactivable` ; hiérarchie d'exceptions (`RessourceIntrouvableException` 404, `RegleMetierViolee` 422, `ConflitException` 409, `AccesInterditException` 403) + `@RestControllerAdvice` en `ProblemDetail` RFC 7807 avec `correlationId` ; `CorrelationIdFilter` (MDC + en-tête `X-Correlation-Id`) ; interface `Notificateur` + enum `TypeNotification` (drapeau `eligibleSms`, sans implémentation de canal).
- Reste : T-03 à T-11, rien d'autre entamé volontairement.
- Décisions : UUID v7 via `@UuidGenerator(algorithm = UuidV7Generator.class)` — Hibernate 6.6.53.Final (embarqué par Spring Boot 3.5.16) n'expose que `AUTO/RANDOM/TIME`, pas encore `Style.VERSION_7`, donc générateur personnalisé (`common/domain/UuidV7Generator`) plutôt qu'attendre une montée de version ; `AuditorAware` renvoie `"system"` en dur jusqu'à T-04 (pas de contexte de sécurité avant).
- Piège rencontré : `@PathVariable` sans nom explicite levait `IllegalArgumentException` (masqué en 500 par le handler générique) faute du flag compilateur `-parameters` — corrigé par `maven.compiler.parameters=true` dans le pom parent. Vérifié en conditions réelles : `docker compose up -d` + `mvn spring-boot:run` → Flyway valide contre Postgres 18.6, `/actuator/health` répond `UP`.

## 2026-08-15 — T-01
- Fait : squelette Maven (pom parent + module `backend`, Spring Boot 3.5.16 / Java 21) ; `docker-compose.yml` avec PostgreSQL 18 + volume persistant ; Flyway `V1__init.sql` (`schema_test`) ; profils `local`/`test` ; `/actuator/health` vérifié `UP` contre le Postgres de docker-compose.
- Reste : tout T-02 à T-11, rien d'autre entamé volontairement.
- Décisions : Postgres 18 (pas 16 comme suggéré dans SPRINT-0.md) pour coller à CLAUDE.md ; volume monté sur `/var/lib/postgresql` (pas `/var/lib/postgresql/data`) — l'image Postgres 18+ a changé sa convention de stockage et refuse de démarrer sur l'ancien point de montage.
- Piège rencontré : `taskkill /F /IM java.exe` en fin de session a tué tous les process Java de la machine (5), pas seulement l'appli Spring Boot lancée pour le test — à éviter, cibler le PID exact la prochaine fois.
