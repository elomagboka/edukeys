package tg.novadigital.edukeys.admission.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import tg.novadigital.edukeys.admission.domain.DemandeAdmission;
import tg.novadigital.edukeys.admission.domain.PieceJointeAdmission;
import tg.novadigital.edukeys.admission.service.CommandeDemandeAdmission;
import tg.novadigital.edukeys.admission.web.AccuseReceptionAdmissionDto;
import tg.novadigital.edukeys.admission.web.DemandeAdmissionDto;
import tg.novadigital.edukeys.admission.web.DonneesDemandeAdmissionDto;

@Mapper(componentModel = "spring", uses = PieceJointeAdmissionMapper.class)
public interface DemandeAdmissionMapper {

    /** Mineur (revue) : {@code DemandeAdmissionService} ne doit jamais importer de DTO de {@code admission.web}. */
    CommandeDemandeAdmission versCommande(DonneesDemandeAdmissionDto dto);

    default AccuseReceptionAdmissionDto versAccuseReception(DemandeAdmission demande) {
        return new AccuseReceptionAdmissionDto(demande.getReference(), "Votre demande a bien été enregistrée. Conservez cette référence.");
    }

    default DemandeAdmissionDto versDto(DemandeAdmission demande, List<PieceJointeAdmission> pieces, PieceJointeAdmissionMapper pieceMapper) {
        return new DemandeAdmissionDto(
                demande.getId(),
                demande.getReference(),
                demande.getAnneeScolaireId(),
                demande.getNiveauId(),
                demande.getClasseId(),
                demande.getNom(),
                demande.getPrenoms(),
                demande.getDateNaissance(),
                demande.getLieuNaissance(),
                demande.getSexe(),
                demande.getNationalite(),
                demande.getEtablissementOrigine(),
                demande.getResponsableNom(),
                demande.getResponsablePrenoms(),
                demande.getResponsableLien().name(),
                demande.getResponsableTelephone(),
                demande.getResponsableEmail(),
                demande.getStatut().name(),
                demande.getCanal().name(),
                demande.getDateSoumission(),
                pieces.stream().map(pieceMapper::versDto).toList());
    }
}
