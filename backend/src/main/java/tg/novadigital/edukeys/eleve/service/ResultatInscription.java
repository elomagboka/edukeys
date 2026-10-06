package tg.novadigital.edukeys.eleve.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Résultat d'une inscription (US-08), consommé une seule fois par le contrôleur. Contient le mot de
 * passe temporaire en clair : {@link CompteEleve#toString()} le masque, pour qu'aucun journal ne le
 * porte par mégarde.
 */
public record ResultatInscription(
        UUID eleveId,
        UUID inscriptionId,
        String matricule,
        String nom,
        String prenoms,
        Reference classe,
        Reference niveau,
        Reference filiere,
        Reference anneeScolaire,
        UUID siteId,
        Instant dateInscription,
        CompteEleve compte) {

    public record Reference(UUID id, String libelle) {
    }

    public record CompteEleve(String identifiantConnexion, String motDePasseTemporaire, Instant expiration) {

        @Override
        public String toString() {
            return "CompteEleve[identifiantConnexion=" + identifiantConnexion + ", motDePasseTemporaire=***, expiration=" + expiration + "]";
        }
    }
}
