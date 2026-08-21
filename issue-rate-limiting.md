## Le problème

`/api/v1/auth/login` est exposé sans authentification, par nature. Sans
limitation de débit, un script teste plusieurs milliers de mots de passe par
minute contre un compte connu. Un compte de direction ou de gestionnaire donne
accès à l'ensemble des dossiers d'élèves, notes et données financières de
l'établissement.

**Absent du backlog produit.** La seule mention de limitation de débit dans le
projet concerne le formulaire public de pré-inscription (US-06, Sprint 2) —
rien pour l'authentification elle-même.

**Échéance : avant la première mise en recette accessible.** Le jour où
`/auth/login` répond depuis Internet, la fenêtre est ouverte.

## Endpoints à couvrir

| Endpoint | Risque |
| :--- | :--- |
| `POST /auth/login` | Force brute sur mot de passe |
| `POST /auth/refresh` | Devinette de jeton |
| Réinitialisation de mot de passe | Énumération de comptes, envoi massif d'emails |
| Pré-inscription publique (US-06) | Remplissage automatisé du formulaire |

## Deux compteurs, pas un

**Par compte** — strict. Protège une cible désignée.

**Par IP** — plus généreux. Contre le balayage : un attaquant qui essaie un
seul mot de passe très courant sur des centaines de comptes ne déclenche
jamais le compteur par compte.

### Attention au contexte togolais

Une limitation par IP trop stricte est dangereuse ici. Les opérateurs mobiles
mutualisent leurs adresses publiques, et un cybercafé ou une école entière
sort sur une seule IP. Un seuil serré bloquerait tous les parents d'un même
opérateur à cause d'un seul maladroit.

Donc : seuil par IP large, seuil par compte serré.

## Attente croissante, pas verrouillage

**Ne pas** verrouiller un compte après N échecs. Un attaquant s'en servirait
pour bloquer volontairement le compte du directeur la veille des bulletins —
le déni de service devient trivial.

Préférer une attente qui double à chaque échec (1 s, 2 s, 4 s, 8 s…, plafonnée
à quelques minutes), remise à zéro après une connexion réussie. L'attaque
devient impraticable, l'utilisateur légitime qui se trompe deux fois ne perd
que quelques secondes.

## Points d'attention

- **Le message d'erreur reste identique** qu'il s'agisse d'un compte inexistant,
  d'un mauvais mot de passe ou d'un dépassement de seuil. Sinon la limitation
  elle-même devient un moyen d'énumérer les comptes valides.
- **Journaliser les échecs** avec l'IP et l'identifiant tenté, sans jamais le
  mot de passe. C'est la seule façon de repérer une attaque en cours.
- **Compteur en mémoire ou partagé ?** Sur Render avec une instance unique, un
  cache mémoire (Caffeine, Bucket4j) suffit. Au passage à plusieurs instances,
  chaque instance aurait son propre compteur et les seuils seraient multipliés
  d'autant — prévoir un stockage partagé à ce moment-là.
- Renvoyer `429 Too Many Requests` avec un en-tête `Retry-After`.

## À produire

- [ ] Choisir le mécanisme (Bucket4j, Resilience4j, ou filtre maison + Caffeine)
- [ ] Filtre appliqué aux endpoints d'authentification
- [ ] Double compteur : par compte (strict) et par IP (large)
- [ ] Attente croissante, remise à zéro après succès
- [ ] Réponse `429` + `Retry-After`, message indifférencié
- [ ] Journalisation des échecs, sans mot de passe
- [ ] Tests : le seuil se déclenche, l'utilisateur légitime n'est pas gêné
- [ ] Étendre à US-06 au Sprint 2

## Références

- `CLAUDE.md`, règle 5
- `docs/SPRINT-0.md`, T-04 (note de hors-périmètre)
