package tg.novadigital.edukeys.testsupport;

import java.util.UUID;

import tg.novadigital.edukeys.eleve.domain.CompteurMatricule;

/**
 * Fabrique de test de {@link CompteurMatricule} (US-08). Année tirée du compteur partagé de
 * {@link FabriqueSupport} — jamais aléatoire : l'unicité absolue (établissement, année) interdit tout
 * doublon lorsque le test générique persiste plusieurs instances dans le même établissement, et un
 * tirage aléatoire finirait par collisionner.
 */
public class FabriqueCompteurMatricule implements FabriqueEntiteEtablissement<CompteurMatricule> {

    @Override
    public Class<CompteurMatricule> typeEntite() {
        return CompteurMatricule.class;
    }

    @Override
    public CompteurMatricule creer(UUID etablissementId) {
        return new CompteurMatricule(null, FabriqueSupport.anneeDebutUnique());
    }
}
