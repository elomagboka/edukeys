# Guide — Développer Edukeys avec Claude Code

## 1. Installation du scaffold

Copie le contenu de ce dossier à la racine de ton projet :

```
edukeys/
├── CLAUDE.md              ← lu automatiquement à chaque session
├── .claude/
│   ├── agents/            ← 4 sous-agents
│   └── commands/          ← 2 slash commands
└── docs/
    ├── backlog.md         ← ton backlog produit (à copier ici)
    └── JOURNAL.md         ← se remplit tout seul via /point
```

Les fichiers de `.claude/agents/` sont chargés **au démarrage uniquement**.
Si tu en ajoutes un pendant une session, redémarre Claude Code.

Adapte `CLAUDE.md` : remplace `<org>` par ton organisation, et tranche le
choix du frontend.

---

## 2. Pourquoi des sous-agents (le vrai mécanisme)

Un sous-agent est une instance Claude séparée, avec **son propre contexte** et
ses propres permissions d'outils. Quand il termine, seul son résumé final
remonte dans ta conversation principale — tout le bruit intermédiaire (lecture
de 30 fichiers, logs Maven, exploration) reste chez lui.

C'est là qu'est l'économie. Exemple concret :

| Sans sous-agent | Avec sous-agent |
| :--- | :--- |
| `mvn test` échoue → 2 000 lignes de stacktrace dans ton contexte principal, pour toujours | Le `testeur` lit les 2 000 lignes chez lui, te rend 4 lignes de diagnostic |

Sur une session de 3 heures, la différence se compte en dizaines de milliers
de tokens — et surtout en qualité : un contexte encombré dégrade les réponses.

Deuxième bénéfice : la séparation des permissions. `architecte` et `reviewer`
sont en lecture seule (`Read, Grep, Glob`). Ils **ne peuvent pas** modifier ton
code par accident.

---

## 3. Le workflow, US par US

```bash
claude                    # démarre à la racine du projet

> /us 08                  # implémente US-08 (matricule)
                          # → l'architecte rend une spec, tu valides
                          # → dev-backend code, testeur teste, reviewer relit

> /point                  # écrit le journal de session
> /clear                  # vide le contexte

> /us 09                  # US suivante, contexte propre
```

**La règle d'or : une US = une session.** Ne laisse jamais une conversation
courir sur cinq stories. Le `/clear` entre deux US n'est pas une perte : le
`CLAUDE.md` et le `JOURNAL.md` rechargent tout ce qui compte.

---

## 4. Les 8 réflexes anti-gaspillage

1. **`CLAUDE.md` court.** C'est un coût fixe payé à chaque session. 150 lignes
   utiles valent mieux que 600 lignes exhaustives. Ce qui est long va dans
   `docs/` et n'est lu que sur demande.

2. **Ne colle jamais de code dans le chat.** Donne le chemin du fichier :
   Claude Code le lit lui-même. Coller 300 lignes que Claude pourrait lire à
   la demande, c'est du gaspillage pur.

3. **Ne fais jamais lire `backlog.md` en entier.** 583 lignes × chaque session,
   c'est absurde. Le `grep -A 25 "## US-08"` de la commande `/us` récupère
   25 lignes au lieu de 583.

4. **`/clear` agressivement.** Entre deux US, après un débogage résolu, après
   une exploration. Le contexte n'est pas un historique à préserver.

5. **Laisse les sous-agents avaler le bruit.** Tests, exploration de code,
   lecture de docs : tout ce qui produit beaucoup de sortie pour peu
   d'information utile.

6. **Valide la spec avant le code.** Cinq minutes de relecture de spec évitent
   trois cycles de correction. Un cycle de correction coûte plus cher que la
   spec initiale.

7. **Choisis le bon modèle.** Opus pour la conception et la review (jugement),
   Sonnet pour l'implémentation (volume). C'est déjà réglé dans les fichiers
   d'agents via le champ `model`.

8. **Réutilise les patterns.** Après US-01, US-02 est presque du copier-coller
   structurel. Dis-le explicitement : « suis exactement le pattern du module
   `academique` ». Claude produit plus vite et plus juste.

---

## 4 bis. Suivi dans GitHub Issues

Le script `scripts/creer-issues.sh` crée en une fois les 13 jalons (un par
sprint), les étiquettes, et les 55 issues : 12 tâches du Sprint 0, 7 du sprint
EDT, et 36 user stories.

```bash
# depuis Git Bash, à la racine du dépôt
bash scripts/creer-issues.sh
```

**Règle de source de vérité, à ne pas transgresser** : les issues portent le
*suivi* (statut, discussion, qui fait quoi). La *spécification* reste dans
`docs/backlog.md`, `docs/SPRINT-0.md` et les ADR.

Chaque issue tient donc en trois lignes et pointe vers le fichier. La raison
est simple : dupliquer les critères d'acceptation dans l'issue garantit qu'ils
divergeront du fichier, et plus personne ne saura lequel fait foi. Le contenu
versionné évolue avec le code, se compare dans les diffs, et reste lisible par
Claude Code sans appel réseau.

La commande `/us` retrouve automatiquement l'issue correspondante et ajoute
`Closes #NN` à la PR : l'issue se ferme donc à la fusion, sans intervention.

Pour une vue Kanban, crée un GitHub Project et branche-le sur le dépôt — les
issues s'y rangent automatiquement par jalon.

---

## 5. Trois pièges qui coûtent cher

**Le module transverse mal placé.** Sécurité, audit, gestion d'erreurs,
pagination : si tu les codes au sprint 1 dans `common/`, tout le reste en
hérite gratuitement. Si tu les improvises au sprint 6, tu réécris six modules.

**Le multi-établissement.** Décision prise : une seule instance de Edukeys
(une application, une base) sert toutes les écoles clientes, séparées par une
colonne `etablissement_id`. Voir `docs/adr/0002-multi-etablissement.md`.

Conséquence à garder en tête tout au long du projet : l'isolation entre écoles
repose entièrement sur ton code, pas sur l'infrastructure. Une requête mal
écrite peut exposer les données d'une école à une autre. C'est pourquoi le
filtrage est automatique (filtre Hibernate) et jamais recopié à la main, et
pourquoi le test d'isolation de T-05 est bloquant en CI.

**L'US-14 sous-estimée.** « Détection automatique des conflits » sur les emplois
du temps, c'est un problème d'optimisation sous contraintes, pas un CRUD. Elle
vaut à elle seule ce que valent US-01 à US-05 réunies. Prévois-lui un sprint
entier, ou dégrade l'exigence en V1 (détection de conflit simple à la saisie,
sans génération automatique).

---

## 6. Ordre de développement recommandé

Ton planning propose 10 sprints. Deux ajustements avant de démarrer :

**Sprint 0 (1 semaine) — à ajouter.** Rien de fonctionnel, tout de structurant :
squelette Maven multi-module, Docker Compose PostgreSQL, Flyway, gestion
globale des erreurs, Spring Security + JWT, audit Envers, pagination, CI.
Chaque US suivante en bénéficie. C'est le sprint le plus rentable du projet.

**US-00 rejoint le Sprint 0.** Elle était absente du planning d'origine alors
qu'elle conditionne le modèle de données entier.

**US-04 passe avant US-06.** Le backlog plaçait un formulaire de
pré-inscription public avant le modèle de permissions.

Le planning complet et corrigé est dans `docs/PLANNING.md` : 13 itérations,
dont un sprint dédié aux emplois du temps.

---

## 7. Décisions prises

Les quatre décisions structurantes sont actées et documentées :

| Sujet | Décision | Référence |
| :--- | :--- | :--- |
| Frontend | React 19 + TypeScript strict, Vite, Ant Design | ADR-0001 |
| Multi-établissement | Une instance partagée, colonne discriminante | ADR-0002 |
| Hébergement | Render, région Frankfurt, PostgreSQL managé | ADR-0007 |
| CI/CD | GitHub Actions pour la qualité, Render pour le déploiement | ADR-0007 |

| Sites et annexes | Niveau `Site` interne à l'établissement | ADR-0005 |
| Notifications | Multicanal, in-app et email en V1, push avec l'app mobile | ADR-0006 |

Toutes les décisions structurantes sont prises. Le planning révisé est dans
`docs/PLANNING.md`.
