package tg.novadigital.edukeys.common.initialisation;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Charge et met en cache {@code referentiel/referentiel-type-tg.json}
 * (système togolais, ADR-0008). Valide l'intégrité des références internes
 * au chargement (une {@link ReferentielType.Filiere} qui référence un niveau
 * inexistant est une erreur de configuration, jamais une exception silencieuse
 * découverte à l'initialisation d'un établissement). La validation est
 * paresseuse et mise en cache : elle a lieu au premier appel réel de
 * {@link #charger()} (première création d'établissement), pas au démarrage
 * de l'application — il n'y a pas de {@code @PostConstruct} ici.
 */
@Component
public class ChargeurReferentielType {

    private static final String CHEMIN_RESSOURCE = "referentiel/referentiel-type-tg.json";

    private final ObjectMapper objectMapper;
    private volatile ReferentielType modeleEnCache;

    public ChargeurReferentielType(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ReferentielType charger() {
        ReferentielType resultat = modeleEnCache;
        if (resultat == null) {
            resultat = lireEtValider();
            modeleEnCache = resultat;
        }
        return resultat;
    }

    private ReferentielType lireEtValider() {
        ReferentielType modele;
        try (InputStream flux = new ClassPathResource(CHEMIN_RESSOURCE).getInputStream()) {
            modele = objectMapper.readValue(flux, ReferentielType.class);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de charger " + CHEMIN_RESSOURCE, e);
        }
        valider(modele);
        return modele;
    }

    private void valider(ReferentielType modele) {
        Set<String> codesCycles = new HashSet<>();
        modele.cycles().forEach(cycle -> codesCycles.add(cycle.code()));

        Set<String> codesNiveaux = new HashSet<>();
        for (ReferentielType.Niveau niveau : modele.niveaux()) {
            codesNiveaux.add(niveau.code());
            if (!codesCycles.contains(niveau.codeCycle())) {
                throw new IllegalStateException(
                        "Référentiel type invalide : le niveau %s référence le cycle inconnu %s."
                                .formatted(niveau.code(), niveau.codeCycle()));
            }
        }

        for (ReferentielType.Filiere filiere : modele.filieres()) {
            if (!codesNiveaux.contains(filiere.codeNiveau())) {
                throw new IllegalStateException(
                        "Référentiel type invalide : la filière %s référence le niveau inconnu %s."
                                .formatted(filiere.code(), filiere.codeNiveau()));
            }
        }
    }
}
