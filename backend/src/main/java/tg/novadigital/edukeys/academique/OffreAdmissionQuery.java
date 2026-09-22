package tg.novadigital.edukeys.academique;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Seul point d'entrée exposé par {@code academique} au module {@code admission}
 * (US-06, CLAUDE.md règle 1) : aucun import d'entité {@code academique}
 * ailleurs. Toutes les méthodes renvoient des records, jamais une entité JPA.
 */
public interface OffreAdmissionQuery {

    /** Offre d'admission ouverte d'un établissement : année courante (active ou en préparation la plus proche) et niveaux/classes actifs. */
    OffreAdmission offreOuverte(UUID etablissementId);

    /** Vrai si {@code classeId} (si fourni) appartient bien à {@code niveauId}, et que les deux existent et sont actifs pour {@code anneeId}. */
    boolean verifierChoix(UUID etablissementId, UUID anneeId, UUID niveauId, UUID classeId);

    /**
     * Libellés de niveaux et classes en une seule requête (CLAUDE.md, règle
     * 10) — jamais un appel par ligne d'une liste de demandes d'admission.
     */
    Map<UUID, String> libelles(Set<UUID> ids);

    record OffreAdmission(UUID anneeScolaireId, String anneeScolaireLibelle, List<NiveauOffre> niveaux) {
    }

    record NiveauOffre(UUID id, String libelle, List<ClasseOffre> classes) {
    }

    record ClasseOffre(UUID id, String libelle) {
    }
}
