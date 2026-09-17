package tg.novadigital.edukeys.academique.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import tg.novadigital.edukeys.academique.domain.AffectationMatiere;
import tg.novadigital.edukeys.academique.domain.Filiere;
import tg.novadigital.edukeys.academique.domain.Matiere;
import tg.novadigital.edukeys.academique.domain.Niveau;
import tg.novadigital.edukeys.academique.web.AffectationMatiereDto;
import tg.novadigital.edukeys.academique.web.MatiereDto;
import tg.novadigital.edukeys.academique.web.MatiereHistoriqueDto;
import tg.novadigital.edukeys.academique.web.RefFiliereDto;
import tg.novadigital.edukeys.academique.web.RefNiveauDto;
import tg.novadigital.edukeys.common.audit.RevisionHistorique;

/**
 * {@code Matiere} ne porte pas de collection {@code @OneToMany} vers ses
 * affectations (voir sa javadoc) : le mapping DTO prend donc en paramètre la
 * liste des affectations actives déjà résolue en amont (anti N+1, spec
 * §Endpoints), plutôt qu'un {@code @Mapping(source = "affectations", ...)}.
 */
@Mapper(componentModel = "spring")
public interface MatiereMapper {

    default MatiereDto versDto(Matiere matiere, List<AffectationMatiere> affectationsActives) {
        return new MatiereDto(
                matiere.getId(),
                matiere.getLibelle(),
                matiere.getCode(),
                matiere.isActif(),
                matiere.getDateDesactivation(),
                affectationsActives.stream().map(this::versAffectationDto).toList());
    }

    default AffectationMatiereDto versAffectationDto(AffectationMatiere affectation) {
        Niveau niveau = affectation.getNiveau();
        Filiere filiere = affectation.getFiliere();
        return new AffectationMatiereDto(
                affectation.getId(),
                niveau != null ? new RefNiveauDto(niveau.getId(), niveau.getLibelle(), niveau.getCode()) : null,
                filiere != null ? new RefFiliereDto(filiere.getId(), filiere.getLibelle(), filiere.getCode()) : null,
                affectation.getCoefficient(),
                affectation.getVolumeHoraire(),
                affectation.getObligatoire());
    }

    default MatiereHistoriqueDto versHistoriqueDto(RevisionHistorique<Matiere> revision) {
        Matiere entite = revision.entite();
        return new MatiereHistoriqueDto(
                revision.numero(),
                revision.date(),
                revision.auteur(),
                revision.type().name(),
                entite.getId(),
                entite.getLibelle(),
                entite.getCode(),
                entite.isActif());
    }
}
