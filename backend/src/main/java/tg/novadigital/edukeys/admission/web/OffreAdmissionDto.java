package tg.novadigital.edukeys.admission.web;

import java.util.List;
import java.util.UUID;

public record OffreAdmissionDto(UUID anneeScolaireId, String anneeScolaireLibelle, boolean admissionsOuvertes,
                                 String etablissementNom, String etablissementLogoUrl, List<NiveauOffreDto> niveaux) {

    public record NiveauOffreDto(UUID id, String libelle, List<ClasseOffreDto> classes) {
    }

    public record ClasseOffreDto(UUID id, String libelle) {
    }
}
