package tg.novadigital.edukeys.academique.mapper;

import org.mapstruct.Mapper;

import tg.novadigital.edukeys.academique.domain.PeriodeAcademique;
import tg.novadigital.edukeys.academique.web.PeriodeAcademiqueDto;
import tg.novadigital.edukeys.academique.web.PeriodeAcademiqueHistoriqueDto;
import tg.novadigital.edukeys.common.audit.RevisionHistorique;

/**
 * {@code enCours} et {@code anneeScolaireLibelle} sont résolus par le
 * service (anti N+1, R7) : jamais devinés ici (spec US-05).
 */
@Mapper(componentModel = "spring")
public interface PeriodeAcademiqueMapper {

    default PeriodeAcademiqueDto versDto(PeriodeAcademique periode, String anneeScolaireLibelle, boolean enCours) {
        return new PeriodeAcademiqueDto(
                periode.getId(),
                periode.getAnneeScolaireId(),
                anneeScolaireLibelle,
                periode.getLibelle(),
                periode.getType(),
                periode.getOrdre(),
                periode.getDateDebut(),
                periode.getDateFin(),
                enCours,
                periode.isActif(),
                periode.getDateDesactivation());
    }

    default PeriodeAcademiqueHistoriqueDto versHistoriqueDto(RevisionHistorique<PeriodeAcademique> revision) {
        PeriodeAcademique entite = revision.entite();
        return new PeriodeAcademiqueHistoriqueDto(
                revision.numero(),
                revision.date(),
                revision.auteur(),
                revision.type().name(),
                entite.getId(),
                entite.getAnneeScolaireId(),
                entite.getLibelle(),
                entite.getType() != null ? entite.getType().name() : null,
                entite.getOrdre(),
                entite.getDateDebut(),
                entite.getDateFin(),
                entite.isActif());
    }
}
