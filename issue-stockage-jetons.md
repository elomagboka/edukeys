## Contexte

T-04 produit deux jetons : un JWT d'accès (15 min) et un jeton de
rafraîchissement opaque (7 jours, révocable). T-11 devra les stocker côté
navigateur — et cette décision n'est pas tranchée.

Ce n'est pas un détail d'implémentation frontend : c'est une décision de
sécurité. Si personne ne la prend, elle se prendra par défaut, et le défaut
est `localStorage`, c'est-à-dire la pire option.

**Échéance : avant T-11.** À formaliser en ADR-0008.

## Le risque à couvrir

Si un attaquant parvient à exécuter du script dans la page (XSS), il peut lire
tout ce qui se trouve dans `localStorage` ou `sessionStorage`. Il ne peut pas
lire un cookie marqué `HttpOnly`.

Sur Edukeys, un jeton de rafraîchissement volé donne sept jours d'accès aux
dossiers d'élèves, aux notes et aux données financières de l'établissement.

## Options

### A — Tout dans localStorage
Simple, fonctionne partout, aucune contrainte de domaine.
**Écartée** : les deux jetons sont lisibles par n'importe quel script injecté.

### B — Accès en mémoire JS, rafraîchissement en cookie HttpOnly *(recommandée)*
Le jeton d'accès vit dans une variable JavaScript et disparaît au
rechargement de page ; le jeton de rafraîchissement est dans un cookie que le
script ne peut pas lire.

- Un XSS ne peut voler qu'un jeton de 15 minutes, non renouvelable.
- Coût : le cookie part automatiquement à chaque requête, ce qui rouvre la
  question du CSRF sur l'endpoint `/refresh` (traitée par `SameSite` et un
  contrôle d'origine).
- Coût : un rechargement de page force un appel à `/refresh`.

### C — Accès en mémoire, rafraîchissement en localStorage
Intermédiaire. Protège l'accès mais pas le rafraîchissement — donc protège
peu, puisque c'est le rafraîchissement qui a de la valeur.

## Dépendance à trancher en même temps : les noms de domaine

L'option B suppose que le cookie puisse être partagé entre le frontend et
l'API. Or, sur Render, les deux services vivent sur des sous-domaines
distincts de `onrender.com` (`edukeys-web-prod` et `edukeys-api-prod`), donc
sur des origines différentes.

Conséquences :

- Cookie inter-sites → `SameSite=None; Secure` obligatoire, ce qui offre une
  protection CSRF moindre et se heurte aux restrictions de certains
  navigateurs sur les cookies tiers.
- Avec un domaine propre et deux sous-domaines d'un même parent
  (`app.edukeys.tg` et `api.edukeys.tg`), le cookie peut être posé sur
  `.edukeys.tg` avec `SameSite=Lax` — nettement plus sain.

**Autrement dit : acheter le domaine et le configurer sur Render conditionne
la faisabilité de l'option recommandée.** À faire avant T-11.

## À produire

- [ ] Décider le nom de domaine et le configurer sur Render (sous-domaines
      `app.` et `api.` d'un même parent)
- [ ] Trancher l'option de stockage
- [ ] Écrire ADR-0008
- [ ] Répercuter dans `frontend/CLAUDE.md` et dans l'agent `dev-frontend`
- [ ] Configurer les attributs du cookie côté backend
      (`HttpOnly`, `Secure`, `SameSite`, `Path=/api/v1/auth`)
- [ ] Ajouter un test vérifiant qu'aucun jeton ne transite par `localStorage`

## Références

- ADR-0007 (hébergement Render, domaines)
- Spec T-04 (émission des jetons)
