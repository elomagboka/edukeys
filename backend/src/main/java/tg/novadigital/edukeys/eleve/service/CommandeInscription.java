package tg.novadigital.edukeys.eleve.service;

import java.util.UUID;

/**
 * Demande d'inscription d'un élève accepté (US-08).
 *
 * @param versionDemande version du dossier lue par le client : une version périmée signale qu'il a été modifié entre-temps
 * @param confirmerHomonyme le client a vu l'alerte d'homonyme et confirme qu'il s'agit bien d'un autre élève
 */
public record CommandeInscription(UUID demandeAdmissionId, UUID classeId, long versionDemande, boolean confirmerHomonyme) {
}
