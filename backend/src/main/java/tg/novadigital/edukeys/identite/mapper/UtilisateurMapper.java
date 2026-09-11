package tg.novadigital.edukeys.identite.mapper;

import org.mapstruct.Mapper;

import tg.novadigital.edukeys.identite.domain.AffectationEtablissement;
import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.service.UtilisateurService;
import tg.novadigital.edukeys.identite.web.CompteCreeDto;
import tg.novadigital.edukeys.identite.web.UtilisateurCompteDto;

/**
 * Mapper entité → DTO du module identité (US-04). {@code Utilisateur} et
 * {@code AffectationEtablissement} ne sortent jamais d'un contrôleur
 * (CLAUDE.md, règle 7) : {@link #versCompteDto} est le seul point de passage
 * qui combine les deux, jamais recopié à la main dans un contrôleur.
 */
@Mapper(componentModel = "spring")
public interface UtilisateurMapper {

    /**
     * Combine le compte et son affectation à l'établissement courant : les
     * rôles et le site affichés sont ceux de <strong>cette</strong>
     * affectation, jamais l'ensemble des affectations du compte sur
     * d'autres établissements.
     */
    default UtilisateurCompteDto versCompteDto(AffectationEtablissement affectation) {
        Utilisateur utilisateur = affectation.getUtilisateur();
        return new UtilisateurCompteDto(
                utilisateur.getId(),
                utilisateur.getEmail(),
                utilisateur.getNomComplet(),
                utilisateur.isActif(),
                utilisateur.isMotDePasseAChanger(),
                affectation.getRoles(),
                affectation.getSiteId(),
                utilisateur.getDateCreation());
    }

    default CompteCreeDto versCompteCreeDto(UtilisateurService.CompteCree compteCree) {
        return new CompteCreeDto(versCompteDto(compteCree.affectation()), compteCree.motDePasseTemporaire());
    }
}
