package tg.novadigital.edukeys.admission;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Port exposé par le module {@code admission} au module {@code eleve} (US-08) :
 * seul point d'entrée sur un dossier d'admission, aucune entité n'en sort
 * (CLAUDE.md, règle 1). Les deux méthodes exigent la transaction de
 * l'inscription ({@code MANDATORY}) : le verrou du dossier et son marquage
 * « inscrit » doivent vivre et mourir avec elle.
 *
 * <p>Opère sur l'établissement du contexte courant : aucune méthode ne prend
 * d'{@code etablissementId} et aucune n'ouvre de {@code PorteeEtablissement}
 * (le contexte est celui de la requête).</p>
 */
public interface DossierAdmissionInscription {

    /**
     * Verrouille le dossier ({@code PESSIMISTIC_WRITE}) et le lit : deux inscriptions
     * simultanées du même dossier se sérialisent ici, la seconde voit {@code eleveId}
     * renseigné.
     *
     * @throws tg.novadigital.edukeys.common.exception.RessourceIntrouvableException si le dossier est
     *         absent, désactivé ou d'un autre établissement
     */
    DossierPourInscription verrouillerPourInscription(UUID demandeId);

    /**
     * Rattache l'élève au dossier ({@code eleveId} et {@code dateInscription}, une seule fois).
     *
     * @throws tg.novadigital.edukeys.common.exception.RegleMetierViolee si le dossier n'est pas ACCEPTEE
     * @throws tg.novadigital.edukeys.common.exception.ConflitException si le dossier est déjà inscrit
     */
    void marquerInscrite(UUID demandeId, UUID eleveId, Instant dateInscription);

    /**
     * Vue d'un dossier pour l'inscription. {@code acceptee} : le statut du dossier est ACCEPTEE
     * ({@code statut} en texte pour les messages, sans exposer l'énumération du module).
     */
    record DossierPourInscription(
            UUID id,
            long version,
            String statut,
            boolean acceptee,
            UUID eleveId,
            UUID anneeScolaireId,
            UUID niveauId,
            UUID classeIdSouhaitee,
            String nom,
            String prenoms,
            LocalDate dateNaissance,
            String lieuNaissance,
            String sexe,
            String nationalite,
            String etablissementOrigine) {
    }
}
