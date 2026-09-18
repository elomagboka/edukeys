package tg.novadigital.edukeys.testsupport;

import java.util.UUID;

import tg.novadigital.edukeys.academique.domain.Matiere;

/** Fabrique de test de {@link Matiere} (US-03). État minimal, sans affectation. */
public class FabriqueMatiere implements FabriqueEntiteEtablissement<Matiere> {

    @Override
    public Class<Matiere> typeEntite() {
        return Matiere.class;
    }

    @Override
    public Matiere creer(UUID etablissementId) {
        String suffixe = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new Matiere(null, "ISOLATION-MATIERE-" + suffixe, null);
    }
}
