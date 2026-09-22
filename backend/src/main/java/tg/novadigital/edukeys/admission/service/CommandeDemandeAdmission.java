package tg.novadigital.edukeys.admission.service;

import java.time.LocalDate;
import java.util.UUID;

import tg.novadigital.edukeys.admission.domain.LienResponsable;

/**
 * Commande de création d'un dossier d'admission, propre à la couche service
 * (mineur, revue) : {@code DemandeAdmissionService} ne doit jamais importer
 * de DTO de {@code admission.web} — la conversion depuis {@code
 * DonneesDemandeAdmissionDto} (validation Bean Validation incluse) se fait au
 * niveau du contrôleur, avant l'appel au service.
 */
public record CommandeDemandeAdmission(
        UUID niveauId,
        UUID classeId,
        String nom,
        String prenoms,
        LocalDate dateNaissance,
        String lieuNaissance,
        String sexe,
        String nationalite,
        String etablissementOrigine,
        String responsableNom,
        String responsablePrenoms,
        LienResponsable responsableLien,
        String responsableTelephone,
        String responsableEmail,
        boolean consentementDonnees) {
}
