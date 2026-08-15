# Journal de développement

> Rempli automatiquement par la commande `/point` en fin de session.
> Se lit au démarrage de la session suivante : il remplace l'historique de conversation.

## 2026-08-15 — T-01
- Fait : squelette Maven (pom parent + module `backend`, Spring Boot 3.5.16 / Java 21) ; `docker-compose.yml` avec PostgreSQL 18 + volume persistant ; Flyway `V1__init.sql` (`schema_test`) ; profils `local`/`test` ; `/actuator/health` vérifié `UP` contre le Postgres de docker-compose.
- Reste : tout T-02 à T-11, rien d'autre entamé volontairement.
- Décisions : Postgres 18 (pas 16 comme suggéré dans SPRINT-0.md) pour coller à CLAUDE.md ; volume monté sur `/var/lib/postgresql` (pas `/var/lib/postgresql/data`) — l'image Postgres 18+ a changé sa convention de stockage et refuse de démarrer sur l'ancien point de montage.
- Piège rencontré : `taskkill /F /IM java.exe` en fin de session a tué tous les process Java de la machine (5), pas seulement l'appli Spring Boot lancée pour le test — à éviter, cibler le PID exact la prochaine fois.
