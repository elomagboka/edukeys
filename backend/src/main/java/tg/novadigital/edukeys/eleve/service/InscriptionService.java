package tg.novadigital.edukeys.eleve.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import tg.novadigital.edukeys.academique.ClasseInscriptionQuery;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.repository.ContrainteViolee;

/**
 * Orchestrateur de l'inscription (US-08), volontairement NON transactionnel : il garantit l'existence de la
 * ligne du compteur de matricule dans une transaction à part, puis délègue l'inscription — toute la
 * logique métier — à {@link InscriptionTransactionnelle}, une transaction unique (appel via le proxy
 * Spring d'un autre bean, jamais par auto-invocation).
 *
 * <p>Motif de {@code DemandeAdmissionService} (US-06) : la transaction à part ne sert QU'À CRÉER LA LIGNE À
 * ZÉRO, jamais à incrémenter. Le conflit d'unicité d'une course sur la première inscription de l'année est
 * toléré ici (la ligne existe alors, committée par la gagnante) ; l'incrément se fait sous verrou dans la
 * transaction principale, dont le rollback annule le numéro. Rien n'est « rejoué » : si l'inscription
 * échoue ensuite, aucun numéro n'a été consommé et rien ne se ré-exécute.</p>
 */
@Service
public class InscriptionService {

    private final ClasseInscriptionQuery classeInscriptionQuery;
    private final CreateurLigneCompteurMatricule createurLigneCompteurMatricule;
    private final InscriptionTransactionnelle inscriptionTransactionnelle;

    public InscriptionService(
            ClasseInscriptionQuery classeInscriptionQuery,
            CreateurLigneCompteurMatricule createurLigneCompteurMatricule,
            InscriptionTransactionnelle inscriptionTransactionnelle) {
        this.classeInscriptionQuery = classeInscriptionQuery;
        this.createurLigneCompteurMatricule = createurLigneCompteurMatricule;
        this.inscriptionTransactionnelle = inscriptionTransactionnelle;
    }

    public ResultatInscription inscrire(CommandeInscription commande) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();

        // Classe inconnue : on ne fait rien ici, la transaction principale répond 404 dans l'ordre des règles.
        Optional<LocalDate> debutAnnee = classeInscriptionQuery.debutAnneeDeLaClasse(commande.classeId());
        if (debutAnnee.isPresent()) {
            assurerLigneCompteur(etablissementId, debutAnnee.get().getYear());
        }
        return inscriptionTransactionnelle.executer(commande);
    }

    private void assurerLigneCompteur(UUID etablissementId, int anneeDebut) {
        try {
            createurLigneCompteurMatricule.assurerExistence(etablissementId, anneeDebut);
        } catch (RuntimeException e) {
            if (!CreateurLigneCompteurMatricule.CONTRAINTE_COMPTEUR.equals(ContrainteViolee.nom(e))) {
                throw e;
            }
            // Course sur la première inscription de l'année : la ligne existe désormais (gagnante committée).
        }
    }
}
