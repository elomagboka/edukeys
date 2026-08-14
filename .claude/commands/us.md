---
description: Implémente une user story de bout en bout (spec → code → tests → review)
argument-hint: [numéro US, ex: 08]
---

Implémente l'US-$1 du backlog.

Déroulé imposé :

1. Récupère UNIQUEMENT la section de cette US :
   `grep -A 25 "## US-$1" docs/backlog.md`
   N'ouvre jamais `docs/backlog.md` en entier.

2. Retrouve son issue GitHub : `gh issue list --search "US-$1 in:title" --json number,title`
   Note son numéro — il servira au commit et à la PR. Si aucune issue
   n'existe, continue sans, et signale-le simplement.

3. Délègue à l'agent **architecte** la conception (entités, endpoints, règles).
   Attends sa spec.

4. Montre-moi la spec et **arrête-toi**. Attends ma validation avant de coder.

5. Après validation : crée la branche `feat/us-$1-<mot-clé>`, puis délègue à
   **dev-backend** l'implémentation.

6. Délègue à **testeur** la couverture des critères d'acceptation.

7. Délègue à **reviewer** la relecture.

8. Propose la PR avec `Closes #<numéro d'issue>` dans le corps, pour que
   l'issue se ferme automatiquement à la fusion.

9. Résume en 5 lignes maximum : fichiers créés, tests passés, points bloquants
   restants. Ne recopie pas le code.
