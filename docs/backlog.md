# 📋 PRODUCT BACKLOG — EDUKEYS

**Version :** 1.0  
**Date :** Décembre 2025  
**Propriétaire :** Product Owner  
**Outil cible :** Jira / Trello / GitHub Projects / Notion

---

## LÉGENDE DES PRIORITÉS

| Symbole | Signification | Description |
| :---: | :--- | :--- |
| 🔴 | **Must Have** | Indispensable pour le MVP. Sans cela, l'application ne fonctionne pas. |
| 🟠 | **Should Have** | Très important, mais peut être reporté en V1.1 si nécessaire. |
| 🟡 | **Could Have** | Agréable à avoir. Prévision pour V2 ou versions ultérieures. |

---

# 🏛️ EPIC 1 : FONDATIONS DU SYSTÈME (ADMIN & CONFIG
*Priorité : MAXIMUM - Sprints 1 & 2*

---

## US-00 : Gestion des établissements scolaire

**En tant que** Administrateur,  
**Je veux** créer et gérer les établissements scolaires,  
**Afin de** matérialiser les établissements scolaire dans le systèmes.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Création d'un établissement scolaire avec adresse (ville, quartier, boite postale, email, logo²)<br>- Modification des informations sur l'établissement scolaire |

---

## US-01 : Gestion des années scolaires

**En tant que** Administrateur,  
**Je veux** créer et gérer les années scolaires,  
**Afin de** structurer le temps académique.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Création d'une année scolaire avec dates de début/fin<br>- Modification des dates<br>- Clôture d'une année<br>- Définition de l'année active en cours |

---

## US-02 : Gestion de la structure académique

**En tant que** Administrateur,  
**Je veux** gérer les niveaux, cycles, filières et classes,  
**Afin de** structurer l'offre de formation.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Création de niveaux (6ème, 5ème, etc.)<br>- Création de cycles (Collège, Lycée)<br>- Création de filières (Scientifique, Littéraire, etc.)<br>- Création de classes avec numérotation (6ème A, 6ème B)<br>- Hiérarchie respectée (Niveau > Cycle > Classe) |

---

## US-03 : Paramétrage des matières

**En tant que** Administrateur,  
**Je veux** paramétrer les matières et les affecter par niveau/filière,  
**Afin de** définir le référentiel pédagogique.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Création d'une matière (ex: Mathématiques)<br>- Association d'une matière à un ou plusieurs niveaux<br>- Association d'une matière à une ou plusieurs filières<br>- Une matière existe indépendamment de ses affectations |

---

## US-04 : Gestion des utilisateurs et rôles (RBAC)

**En tant que** Administrateur,  
**Je veux** gérer les utilisateurs et leurs rôles,  
**Afin de** sécuriser l'accès aux données.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Création de comptes utilisateurs<br>- Attribution de rôles (Admin, Direction, Gestionnaire, Enseignant, Parent, Élève)<br>- Droits d'accès différenciés selon les rôles<br>- Désactivation logique d'un compte |

---

## US-05 : Paramétrage des périodes académiques

**En tant que** Administrateur,  
**Je veux** paramétrer les périodes académiques (trimestres/semestres),  
**Afin de** découper l'année pour les évaluations.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Définition du type de période (Trimestre, Semestre)<br>- Définition des dates de début/fin<br>- Association à une année scolaire<br>- Une période active en cours |

---

# 👨‍🎓 EPIC 2 : CYCLE DE VIE DE L'ÉLÈVE (ADMISSIONS & DOSSIERS)
*Priorité : MAXIMUM - Sprints 3 à 5*

---

## US-06 : Pré-inscription en ligne

**En tant que** Parent ou Administrateur,  
**Je veux** effectuer une pré-inscription en ligne,  
**Afin de** simplifier la saisie des demandes d'admission.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Formulaire de saisie des informations personnelles (nom, prénoms, date de naissance, etc.)<br>- Choix du niveau/classe souhaité<br>- Upload de pièces jointes (acte de naissance, bulletins)<br>- Enregistrement du statut "En attente" |

---

## US-07 : Traitement des dossiers d'admission

**En tant que** Administrateur,  
**Je veux** traiter les dossiers (valider/refuser/mettre en liste d'attente),  
**Afin de** gérer les admissions.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Changement de statut (Accepté, Refusé, Liste d'attente)<br>- Saisie d'une observation sur la décision<br>- Notification automatique au parent de la décision<br>- Historisation des décisions |

---

## US-08 : Génération du matricule et création du dossier élève

**En tant que** Administrateur,  
**Je veux** générer automatiquement le matricule et créer le dossier élève,  
**Afin de** officialiser l'inscription.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Génération automatique d'un numéro matricule unique à la validation<br>- Création automatique du dossier élève<br>- Affectation à une classe, une filière et une année scolaire<br>- Activation du compte élève |

---

## US-09 : Enregistrement des responsables légaux

**En tant que** Administrateur,  
**Je veux** enregistrer les responsables légaux et les lier aux élèves,  
**Afin de** assurer le lien familial.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Un élève peut avoir un ou plusieurs responsables<br>- Saisie des informations (nom, prénoms, profession, lien de parenté, coordonnées)<br>- Mise à jour des coordonnées à tout moment<br>- Association aux élèves concernés |

---

## US-10 : Consultation et mise à jour du dossier élève

**En tant que** Administrateur,  
**Je veux** consulter et mettre à jour le dossier complet de l'élève,  
**Afin de** avoir une vue à 360°.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Vue détaillée avec onglets : Identité, Scolarité, Finances, Documents<br>- Modification des informations personnelles<br>- Mise à jour des informations académiques<br>- Historisation des modifications |

---

## US-11 : Gestion des mouvements (Transfert, Radiation, Changement)

**En tant que** Administrateur,  
**Je veux** gérer les mouvements (transfert, radiation, changement de classe),  
**Afin de** suivre le parcours de l'élève.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🟠 Should Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Gestion du transfert vers un autre établissement<br>- Gestion de la radiation avec motif<br>- Changement de classe en cours d'année<br>- Historisation des mouvements<br>- Désactivation logique (pas de suppression physique) |

---

## US-12 : Recherche multicritères des élèves

**En tant que** Administrateur,  
**Je veux** rechercher des élèves par multicritères,  
**Afin de** gagner du temps.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Moteur de recherche avec filtres (Matricule, Nom/Prénoms, Classe, Année scolaire, Statut)<br>- Affichage des résultats en liste<br>- Export de la liste au format Excel<br>- Accès rapide au profil de l'élève |

---

# 👨‍🏫 EPIC 3 : PILOTAGE PÉDAGOGIQUE (COURS, PRÉSENCES, NOTES)
*Priorité : ÉLEVÉE - Sprints 6 à 9*

---

## US-13 : Affectation des enseignants aux classes et matières

**En tant que** Administrateur,  
**Je veux** affecter les enseignants aux classes et matières,  
**Afin de** définir les responsabilités pédagogiques.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Un enseignant peut avoir plusieurs affectations<br>- Vérification des conflits de spécialité<br>- Affectation à une année scolaire<br>- Historisation des affectations |

---

## US-14 : Gestion des emplois du temps

**En tant que** Administrateur ou Enseignant,  
**Je veux** gérer les emplois du temps (classe & enseignant) et résoudre les conflits,  
**Afin de** organiser les cours.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Création d'emplois du temps par classe et par enseignant<br>- Visualisation hebdomadaire et mensuelle<br>- Détection automatique des conflits (enseignant, salle, horaire)<br>- Diffusion automatique aux élèves et enseignants |

---

## US-15 : Appel numérique (Présences/Absences/Retards)

**En tant que** Enseignant,  
**Je veux** faire l'appel numérique pour mes séances,  
**Afin de** suivre l'assiduité.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Saisie rapide par séance de cours<br>- Enregistrement des absences et des retards<br>- Justification des absences<br>- Historique des présences par élève et par enseignant<br>- Alerte automatique aux parents en cas d'absence répétée |

---

## US-16 : Création et publication des devoirs

**En tant que** Enseignant,  
**Je veux** créer et publier des devoirs (avec consignes et délais),  
**Afin de** assigner du travail aux élèves.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Création de devoirs avec consignes<br>- Définition des délais de remise<br>- Upload de ressources pédagogiques<br>- Publication et notification automatique aux élèves |

---

## US-17 : Soumission des devoirs en ligne par les élèves

**En tant que** Élève,  
**Je veux** soumettre mes devoirs en ligne,  
**Afin de** respecter les consignes de l'enseignant.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Upload de fichiers<br>- Respect de la date limite (bloquante ou avec pénalité)<br>- Suivi des devoirs rendus et non rendus<br>- Consultation des feedbacks de l'enseignant |

---

## US-18 : Saisie des notes et paramétrage des coefficients

**En tant que** Enseignant,  
**Je veux** saisir les notes et paramétrer les coefficients,  
**Afin de** évaluer les élèves.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Saisie des notes par élève<br>- Paramétrage des coefficients et barèmes<br>- Calcul automatique des moyennes (par matière, période, élève)<br>- Modification des notes avec historisation |

---

## US-19 : Validation des notes avant publication

**En tant que** Enseignant,  
**Je veux** valider les notes avant publication,  
**Afin de** garantir l'intégrité des résultats.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Statut "En attente" / "Validé"<br>- Blocage de la modification après validation (sauf autorisation admin)<br>- Notification aux parents après validation<br>- Traçabilité des modifications |

---

## US-20 : Génération des bulletins de notes

**En tant que** Administrateur ou Direction,  
**Je veux** générer des bulletins de notes au format PDF,  
**Afin de** les transmettre aux parents.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Génération PDF avec logo de l'établissement<br>- Intégration des notes, moyennes, appréciations<br>- Intégration des observations pédagogiques<br>- Téléchargement et impression |

---

# 💰 EPIC 4 : GESTION FINANCIÈRE (CAISSE & FACTURATION)
*Priorité : ÉLEVÉE - Sprints 10 à 12*

---

## US-21 : Paramétrage des frais scolaires

**En tant que** Administrateur financier,  
**Je veux** paramétrer les frais scolaires par niveau/classe,  
**Afin de** configurer la facturation.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Définition des types de frais (Inscription, Scolarité, Cantine, Examen, etc.)<br>- Définition des montants par niveau/classe<br>- Gestion des échéanciers (paiement unique ou fractionné)<br>- Activation/désactivation des frais |

---

## US-22 : Enregistrement des paiements

**En tant que** Caissier,  
**Je veux** enregistrer les paiements effectués par les élèves,  
**Afin de** créditer le compte de l'élève.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Saisie du mode de paiement (Espèces, Mobile Money, Chèque, Virement)<br>- Affectation automatique aux frais correspondants<br>- Impression du reçu de paiement<br>- Gestion des paiements partiels et trop-perçus |

---

## US-23 : Consultation du solde par le parent

**En tant que** Parent,  
**Je veux** consulter le solde de mon enfant (payé / restant dû),  
**Afin de** suivre ma situation financière.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Affichage clair du solde (payé, restant dû)<br>- Accès aux factures et reçus générés<br>- Visualisation des échéances de paiement<br>- Alerte en cas d'impayé |

---

## US-24 : Gestion des dépenses

**En tant que** Caissier,  
**Je veux** gérer les dépenses de l'établissement,  
**Afin de** assurer le suivi des sorties d'argent.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🟠 Should Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Enregistrement des dépenses (date, montant, mode, bénéficiaire)<br>- Catégorisation des dépenses (Fournitures, Salaires, Maintenance, etc.)<br>- Ajout de justificatifs<br>- Export des états de dépenses |

---

## US-25 : Clôture de caisse

**En tant que** Caissier,  
**Je veux** clôturer la caisse quotidiennement,  
**Afin de** sécuriser les opérations et générer le journal de caisse.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🟠 Should Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Clôture journalière<br>- Impossibilité de saisir de nouvelles opérations sur cette date après clôture<br>- Génération du journal de caisse<br>- Génération de l'état de caisse |

---

## US-26 : Tableau de bord financier pour la Direction

**En tant que** Direction,  
**Je veux** consulter un tableau de bord financier (recettes/dépenses/taux de recouvrement),  
**Afin de** piloter la santé financière.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Visualisation graphique des recettes par période<br>- Visualisation des dépenses par catégorie<br>- Suivi du taux de recouvrement des frais scolaires<br>- Filtrage par période (journalière, mensuelle, annuelle) |

---

# 📱 EPIC 5 : PORTAILS UTILISATEURS (PARENTS, ÉLÈVES, ENSEIGNANTS)
*Priorité : ÉLEVÉE - Sprints 13 à 15*

---

## US-27 : Portail Parent - Suivi de la scolarité

**En tant que** Parent,  
**Je veux** un portail dédié pour consulter les notes, absences, emploi du temps de mes enfants,  
**Afin de** suivre leur scolarité en temps réel.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Vue unique par enfant<br>- Onglets : Notes, Absences, Devoirs, Emploi du temps<br>- Consultation des bulletins et appréciations<br>- Accès aux documents scolaires |

---

## US-28 : Alertes et notifications automatiques

**En tant que** Parent,  
**Je veux** recevoir des alertes automatiques (absence répétée, note basse, impayé),  
**Afin de** agir rapidement.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Notifications In-App<br>- Notifications Push (mobile)<br>- Notifications par email<br>- Gestion des préférences de notification |

---

## US-29 : Messagerie Parents-Enseignants

**En tant que** Parent,  
**Je veux** échanger avec les enseignants via une messagerie sécurisée,  
**Afin de** discuter du suivi de mon enfant.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🟠 Should Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Messages privés entre parents et enseignants<br>- Messages entre parents et administration<br>- Historique conservé des échanges<br>- Pièces jointes autorisées |

---

## US-30 : Portail Élève - Suivi personnel

**En tant que** Élève,  
**Je veux** consulter mes notes, mon agenda et mes devoirs à rendre,  
**Afin de** organiser mon travail personnel.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Visualisation de l'emploi du temps personnel<br>- Liste des devoirs non rendus<br>- Consultation des notes et appréciations<br>- Consultation des supports de cours |

---

## US-31 : Portail Enseignant - Tableau de bord

**En tant que** Enseignant,  
**Je veux** un tableau de bord affichant mes classes, mes cours du jour et mes actions en attente,  
**Afin de** organiser ma journée.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Vue "Ma journée" avec les cours du jour<br>- Accès rapide à l'appel, aux notes et aux devoirs<br>- Visualisation des notifications récentes<br>- Accès aux statistiques pédagogiques |

---

## US-32 : Prise de rendez-vous en ligne

**En tant que** Parent,  
**Je veux** prendre des rendez-vous en ligne avec les enseignants,  
**Afin de** organiser les réunions.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🟡 Could Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Affichage des créneaux disponibles des enseignants<br>- Demande de rendez-vous par le parent<br>- Confirmation, report ou annulation par l'enseignant<br>- Calendrier des rendez-vous |

---

# 📊 EPIC 6 : PILOTAGE & REPORTING (TABLEAU DE BORD ADMIN)
*Priorité : MOYENNE - Sprints 16 à 18*

---

## US-33 : Tableau de bord global pour la Direction

**En tant que** Direction,  
**Je veux** un tableau de bord global avec les KPI de l'établissement,  
**Afin de** avoir une vision stratégique.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🔴 Must Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Affichage des indicateurs clés : Nb total d'élèves, Nb d'enseignants, Nb de classes<br>- Répartition des élèves par niveau et par classe<br>- Taux de présence global<br>- État de l'année scolaire en cours |

---

## US-34 : Identification des classes à risque

**En tant que** Direction,  
**Je veux** visualiser les classes à risque (fort taux d'absence, mauvais résultats),  
**Afin de** intervenir préventivement.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🟠 Should Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Système de feux tricolores (Vert/Orange/Rouge) sur les classes<br>- Alertes sur les classes avec taux d'absence > seuil défini<br>- Alertes sur les classes avec moyennes faibles<br>- Accès direct aux détails de la classe |

---

## US-35 : Génération de rapports d'activité

**En tant que** Gestionnaire,  
**Je veux** générer des rapports d'activité (admissions, recettes, réussite),  
**Afin de** les archiver ou partager.

| Critère | Description |
| :--- | :--- |
| **Priorité** | 🟠 Should Have |
| **Story Points** | À estimer |
| **Critères d'acceptation** | - Rapports pré-paramétrés : Admissions, Recettes, Réussite<br>- Export au format PDF<br>- Export au format Excel<br>- Filtrage par période |

---

# 📅 PROPOSITION DE PLANNING SPRINTS (MVP)

| Sprint | Période | Objectif | User Stories |
| :--- | :--- | :--- | :--- |
| **Sprint 1** | Semaines 1-2 | Fondations | US-01, US-02, US-03, US-04 |
| **Sprint 2** | Semaines 3-4 | Périodes & Admissions | US-05, US-06, US-07 |
| **Sprint 3** | Semaines 5-6 | Dossiers élèves | US-08, US-09, US-10, US-12 |
| **Sprint 4** | Semaines 7-8 | Mouvements & Affectations | US-11, US-13 |
| **Sprint 5** | Semaines 9-10 | Emplois du temps & Présences | US-14, US-15 |
| **Sprint 6** | Semaines 11-12 | Devoirs & Notes | US-16, US-17, US-18 |
| **Sprint 7** | Semaines 13-14 | Validation notes & Bulletins | US-19, US-20 |
| **Sprint 8** | Semaines 15-16 | Paramétrage financier & Paiements | US-21, US-22 |
| **Sprint 9** | Semaines 17-18 | Portails (Parents, Élèves, Enseignants) | US-23, US-27, US-30, US-31 |
| **Sprint 10** | Semaines 19-20 | Dashboard & Finalisation | US-26, US-33 |

---

## 📌 RÉCAPITULATIF DES STORIES PAR PRIORITÉ

| Priorité | Nombre de Stories | Liste des IDs |
| :--- | :---: | :--- |
| 🔴 **Must Have** | 25 | US-01, 02, 03, 04, 05, 06, 07, 08, 09, 10, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 26, 27, 28, 30, 31, 33 |
| 🟠 **Should Have** | 6 | US-11, 24, 25, 29, 34, 35 |
| 🟡 **Could Have** | 1 | US-32 |

---

**Fin du Product Backlog**

*Ce document est vivant et sera régulièrement mis à jour lors des cérémonies Scrum (Backlog Refinement, Sprint Planning).*