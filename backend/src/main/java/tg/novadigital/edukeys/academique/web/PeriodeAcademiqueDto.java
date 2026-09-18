package tg.novadigital.edukeys.academique.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import tg.novadigital.edukeys.academique.domain.TypePeriode;

/**
 * {@code enCours} : dérivé au moment de la lecture (R7), jamais persisté.
 * {@code anneeScolaireLibelle} : injecté par le service (résolution en lot,
 * anti N+1), jamais deviné par le mapper.
 */
public record PeriodeAcademiqueDto(
        UUID id,
        UUID anneeScolaireId,
        String anneeScolaireLibelle,
        String libelle,
        TypePeriode type,
        int ordre,
        LocalDate dateDebut,
        LocalDate dateFin,
        boolean enCours,
        boolean actif,
        Instant dateDesactivation) {
}
