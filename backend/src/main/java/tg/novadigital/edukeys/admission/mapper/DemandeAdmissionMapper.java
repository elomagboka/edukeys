package tg.novadigital.edukeys.admission.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import tg.novadigital.edukeys.admission.domain.DecisionAdmission;
import tg.novadigital.edukeys.admission.domain.DemandeAdmission;
import tg.novadigital.edukeys.admission.domain.PieceJointeAdmission;
import tg.novadigital.edukeys.admission.service.CommandeDemandeAdmission;
import tg.novadigital.edukeys.admission.web.AccuseReceptionAdmissionDto;
import tg.novadigital.edukeys.admission.web.DemandeAdmissionDto;
import tg.novadigital.edukeys.admission.web.DonneesDemandeAdmissionDto;

@Mapper(componentModel = "spring", uses = {PieceJointeAdmissionMapper.class, DecisionAdmissionMapper.class})
public interface DemandeAdmissionMapper {

    /** Mineur (revue) : {@code DemandeAdmissionService} ne doit jamais importer de DTO de {@code admission.web}. */
    CommandeDemandeAdmission versCommande(DonneesDemandeAdmissionDto dto);

    /** 3e revue, point 1 : le code de suivi opaque, jamais la référence séquentielle. */
    default AccuseReceptionAdmissionDto versAccuseReception(DemandeAdmission demande) {
        return new AccuseReceptionAdmissionDto(demande.getCodeSuivi(), "Votre demande a bien été enregistrée. Conservez ce code de suivi.");
    }

    /** Dossier fraîchement créé (US-06) : aucune décision encore prise. */
    default DemandeAdmissionDto versDto(DemandeAdmission demande, List<PieceJointeAdmission> pieces, PieceJointeAdmissionMapper pieceMapper) {
        return versDto(demande, pieces, List.of(), pieceMapper, null);
    }

    /** Détail enrichi (US-07) : décisions du journal, mappées via {@code decisionMapper}. */
    default DemandeAdmissionDto versDto(
            DemandeAdmission demande,
            List<PieceJointeAdmission> pieces,
            List<DecisionAdmission> decisions,
            PieceJointeAdmissionMapper pieceMapper,
            DecisionAdmissionMapper decisionMapper) {
        return new DemandeAdmissionDto(
                demande.getId(),
                demande.getReference(),
                demande.getCodeSuivi(),
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
                demande.getVersion(),
                demande.getDateDecision(),
                demande.getMotifDecision(),
                demande.getDecidePar(),
                demande.getEleveId(),
                demande.getDateInscription(),
                pieces.stream().map(pieceMapper::versDto).toList(),
                decisionMapper == null ? List.of() : decisions.stream().map(decisionMapper::versDto).toList());
    }
}
