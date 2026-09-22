package tg.novadigital.edukeys.etablissement.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;
import tg.novadigital.edukeys.etablissement.EtablissementPublicQuery;
import tg.novadigital.edukeys.etablissement.domain.Etablissement;
import tg.novadigital.edukeys.etablissement.repository.EtablissementRepository;
import tg.novadigital.edukeys.etablissement.repository.LogoEtablissementRepository;

/**
 * Implémentation d'{@link EtablissementPublicQuery} (US-06) : résolution
 * publique par code, hors authentification. {@link Etablissement} ne sort
 * jamais de ce module — {@link EtablissementPublicQuery.EtablissementPublic}
 * en sortie.
 */
@Service
public class EtablissementPublicQueryService implements EtablissementPublicQuery {

    private final EtablissementRepository etablissementRepository;
    private final LogoEtablissementRepository logoEtablissementRepository;

    public EtablissementPublicQueryService(
            EtablissementRepository etablissementRepository,
            LogoEtablissementRepository logoEtablissementRepository) {
        this.etablissementRepository = etablissementRepository;
        this.logoEtablissementRepository = logoEtablissementRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EtablissementPublic> resoudreParCode(String code) {
        return etablissementRepository.findByCodeIgnoreCaseAndActifTrue(code)
                .map(etablissement -> new EtablissementPublic(
                        etablissement.getId(),
                        etablissement.getNom(),
                        logoUrl(etablissement),
                        etablissement.isAdmissionsOuvertes()));
    }

    private String logoUrl(Etablissement etablissement) {
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissement.getId())) {
            boolean aUnLogo = logoEtablissementRepository.findByEtablissementIdAndActifTrue(etablissement.getId()).isPresent();
            return aUnLogo ? "/api/v1/etablissements/" + etablissement.getId() + "/logo" : null;
        }
    }
}
