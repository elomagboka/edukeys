package tg.novadigital.edukeys.testsupport;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import tg.novadigital.edukeys.admission.domain.CompteurReferenceAdmission;

/**
 * Fabrique de test de {@link CompteurReferenceAdmission} (US-06). Année
 * aléatoire à chaque appel (comme {@link FabriqueAnneeScolaire}) : la
 * contrainte d'unicité {@code (etablissement, annee)} interdit tout doublon
 * lorsque le test générique persiste plusieurs instances dans le même
 * établissement.
 */
public class FabriqueCompteurReferenceAdmission implements FabriqueEntiteEtablissement<CompteurReferenceAdmission> {

    @Override
    public Class<CompteurReferenceAdmission> typeEntite() {
        return CompteurReferenceAdmission.class;
    }

    @Override
    public CompteurReferenceAdmission creer(UUID etablissementId) {
        int annee = 2000 + ThreadLocalRandom.current().nextInt(0, 1000);
        return new CompteurReferenceAdmission(null, annee);
    }
}
