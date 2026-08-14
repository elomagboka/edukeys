# ADR-0006 — Notifications multicanal

**Statut** : accepté · **Date** : août 2026

## Décision

Une abstraction multicanal posée dès le Sprint 0, des canaux livrés
progressivement : in-app, email et SMS en V1 ; push mobile avec l'application
mobile.

---

## Le principe : abstraction tôt, canaux tard

Poser l'abstraction coûte quelques heures au Sprint 0. La rétro-adapter après
que quinze endroits du code appellent directement un service d'email coûte
plusieurs jours et introduit des régressions.

À l'inverse, développer le push avant l'existence de l'application mobile
produit du code que **personne ne peut tester** : pas d'appareil pour recevoir
la notification, pas de jeton à enregistrer, aucune boucle de retour. Ce code
dormirait plusieurs mois avant sa première exécution réelle, et il faudrait le
déboguer au moment le plus chargé — la sortie de l'application mobile.

D'où la règle : **le code métier ne connaît jamais le canal.**

```
service métier → Notificateur.envoyer(destinataire, TypeNotification, données)
                      │
                      ├── CanalInApp      (V1)
                      ├── CanalEmail      (V1)
                      ├── CanalSms        (V1)
                      └── CanalPush       (phase mobile)
```

Ajouter un canal devient l'ajout d'une classe et d'une ligne de configuration,
sans toucher à un seul service métier.

## Modèle de données

- `Notification` — destinataire, type, données, date de création
- `EnvoiNotification` — une ligne par canal tenté : statut, date, erreur,
  nombre de tentatives. Une notification échouée sur un canal reste tracée.
- `PreferenceNotification` — par utilisateur et par type de notification, les
  canaux activés. Les préférences sont exigées par le backlog (US-28).
- `AppareilEnregistre` — jeton d'appareil, plateforme, date de dernière
  activité. Créé dès le Sprint 0 même s'il reste vide jusqu'à l'application
  mobile : sa présence évite une migration sur une table volumineuse plus tard.

## Envoi asynchrone, toujours

Une notification n'est **jamais** envoyée dans le fil de la requête HTTP.
L'enregistrement d'une note ne doit pas attendre un serveur SMTP.

Le service métier écrit la notification en base et rend la main. Un traitement
séparé dépile et envoie, avec reprise sur échec et abandon après N tentatives.

**Piège lié au multi-établissement** : ce traitement s'exécute sans contexte de
sécurité, donc sans établissement (ADR-0002). Il doit ouvrir explicitement un
contexte par établissement. Une notification envoyée au mauvais parent est un
incident grave et parfaitement évitable.

## Groupement obligatoire

Une notification par note publiée produit vingt messages en une soirée, et le
parent désactive tout. Deux règles :

- Les notifications de même type sont **groupées** sur une fenêtre configurable
  (« 4 nouvelles notes en mathématiques » plutôt que quatre messages).
- Les notifications critiques — absence non justifiée, échéance de paiement —
  partent immédiatement, sans groupement.

Cette distinction est portée par le type de notification, pas par le code
appelant.

## Les canaux, un par un

### In-app — à faire en premier

Aucune dépendance externe, testable immédiatement, aucun coût d'envoi.
C'est aussi le seul canal qui fonctionne quand tout le reste échoue.

Il sert de canal de référence : si une notification n'apparaît pas in-app,
c'est le moteur qui est en cause, pas le canal.

### Email — V1

Service transactionnel externe (Azure Communication Services, ou un
fournisseur type Brevo/SendGrid), jamais un SMTP monté à la main : la
délivrabilité dépend d'une réputation d'expéditeur qu'on ne construit pas
soi-même.

À configurer côté domaine : SPF, DKIM et DMARC. Sans eux, les emails d'Edukeys
finiront en indésirables, et le problème sera diagnostiqué trois mois trop
tard.

### Push mobile — avec l'application mobile, pas avant

Firebase Cloud Messaging couvre Android et iOS. Sur Azure, Notification Hubs
peut s'intercaler, mais n'apporte de valeur qu'à volume élevé — FCM en direct
suffit largement au démarrage.

Points de vigilance :

- Les jetons d'appareil **expirent et tournent**. Il faut les rafraîchir au
  démarrage de l'application et purger ceux qui échouent, sinon la table
  grossit indéfiniment avec des jetons morts.
- Un utilisateur a plusieurs appareils. La notification part sur tous.
- Le push ne garantit pas la livraison. Il complète l'in-app, il ne le remplace
  jamais : l'application doit toujours pouvoir afficher les notifications
  non lues sans dépendre du push.

### SMS — retenu en V1, aux côtés de l'email

Décision motivée par le contexte togolais. Le push suppose un smartphone
**et** l'application installée **et** des données actives — pour des parents
d'élèves, cette intersection est étroite. Le SMS atteint tout le monde,
immédiatement, sans rien installer.

Il ne dépend d'aucune application mobile : il peut donc être livré dès la V1,
avant le push.

**Le SMS coûte de l'argent à chaque envoi.** C'est le seul canal payant à
l'unité, et cela dicte toutes les règles qui suivent.

#### Réservé aux notifications à forte valeur

Un type de notification porte explicitement le drapeau « éligible SMS ».
Par défaut, il ne l'est pas. Sont éligibles :

- absence non justifiée, le jour même
- échéance ou relance de paiement
- convocation
- fermeture exceptionnelle de l'établissement

Ne le sont pas : nouvelles notes, devoirs publiés, messages de la messagerie.
Ces derniers passent par l'in-app et l'email.

#### Le piège des accents — à connaître avant d'écrire le premier gabarit

Un SMS standard contient 160 caractères, mais uniquement dans l'alphabet
GSM-7. Dès qu'un caractère en sort — **é, è, à, ç, ù** — le message bascule en
UCS-2 et tombe à **70 caractères**, souvent découpé en plusieurs SMS facturés
séparément.

Conséquence concrète : un message en français accentué peut coûter deux à
trois fois plus cher que le même message sans accents, pour un contenu
identique.

Règle retenue : **les gabarits SMS sont rédigés sans accents**, et un test
automatisé vérifie que chaque gabarit reste dans l'alphabet GSM-7 et sous
160 caractères après substitution des variables. Ce test se met en place une
fois et évite une dérive de facture invisible.

L'email et l'in-app conservent bien entendu les accents.

#### Numéros de téléphone

Stockage au format international E.164 uniquement (`+228XXXXXXXX` pour le
Togo). Les parents saisiront `90 12 34 56`, `00228 90123456` ou
`+228-90-12-34-56` : la normalisation se fait **à la saisie**, pas à l'envoi.
Un numéro non normalisable est refusé au moment de la saisie, quand
l'utilisateur peut encore le corriger.

Prévoir plusieurs numéros par responsable légal, avec un numéro principal :
c'est la réalité des familles.

#### Plafond de dépense — non négociable

Un compteur d'envois par établissement et par mois, avec un plafond
configurable et une alerte à 80 %. Au-delà, les envois SMS sont suspendus et
basculés sur l'email.

**Raison** : une boucle mal écrite qui envoie mille SMS ne produit pas un bug,
elle produit une facture. Contrairement à tous les autres canaux, l'erreur a
un coût immédiat et irréversible. Ce plafond fait partie de l'US, pas d'une
amélioration ultérieure.

#### Fenêtre horaire

Aucun envoi entre 21h et 6h, sauf urgence explicite. Un SMS réveille son
destinataire ; un email non.

#### Choix du fournisseur

Passer par un agrégateur qui couvre réellement les deux opérateurs togolais.
Critères de sélection, par ordre d'importance :

1. couverture effective des opérateurs locaux, vérifiée par un test réel
2. accusé de réception de livraison, sans quoi on ignore ce qui est arrivé
3. tarif au message vers le Togo
4. nom d'expéditeur alphanumérique — affichage « EDUKEYS » plutôt qu'un
   numéro. Son enregistrement peut être soumis à des formalités auprès des
   opérateurs ou du régulateur : à vérifier tôt, les délais sont longs.

L'abstraction `Notificateur` rend le fournisseur interchangeable : le changer
ne touchera aucun service métier.

## Découpage de l'US-28

| Lot | Contenu | Quand |
| :--- | :--- | :--- |
| Abstraction | Interface `Notificateur`, types de notification | Sprint 0 (T-02) |
| US-28a | Moteur, file d'attente, groupement, canal in-app | Sprint 10 |
| US-28b | Canal email, préférences utilisateur | Sprint 10 |
| US-28d | Canal SMS, plafond de dépense, gabarits GSM-7 | Sprint 10 |
| US-28c | Canal push, enregistrement des appareils | Phase mobile |

## Application mobile

Prévue après la V1 web. Elle réutilise la même API REST — c'est une raison
supplémentaire de soigner le contrat OpenAPI (ADR-0001, ADR-0004).

Deux conséquences à anticiper dès maintenant, sans coût :

- Le rafraîchissement du jeton d'authentification doit fonctionner sur une
  session longue : une application mobile n'est pas rouverte tous les jours.
- Les endpoints du portail parent doivent renvoyer des charges utiles compactes
  et paginées. Un forfait de données togolais n'est pas une fibre européenne.

Le choix de la technologie mobile fera l'objet d'un ADR distinct, au moment de
la décision.
