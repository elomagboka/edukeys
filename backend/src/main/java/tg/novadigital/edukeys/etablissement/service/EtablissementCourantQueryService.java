package tg.novadigital.edukeys.etablissement.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.etablissement.EtablissementCourantQuery;
import tg.novadigital.edukeys.etablissement.repository.EtablissementRepository;

/** Implémentation d'{@link EtablissementCourantQuery} (US-08). {@code Etablissement} n'est pas filtré : lecture par l'identifiant du contexte. */
@Service
public class EtablissementCourantQueryService implements EtablissementCourantQuery {

    private final EtablissementRepository etablissementRepository;

    public EtablissementCourantQueryService(EtablissementRepository etablissementRepository) {
        this.etablissementRepository = etablissementRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ParametresInscription parametresInscription() {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        return etablissementRepository.findById(etablissementId)
                .map(e -> new ParametresInscription(e.getCode(), e.getFuseauHoraire()))
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.ETABLISSEMENT_INTROUVABLE, "Établissement introuvable."));
    }
}
