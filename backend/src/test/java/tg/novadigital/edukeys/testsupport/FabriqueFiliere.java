package tg.novadigital.edukeys.testsupport;

import java.util.UUID;

import tg.novadigital.edukeys.academique.domain.Filiere;

/**
 * Fabrique de test de {@link Filiere} (US-02). {@code cycle} nullable (D4) :
 * la fabrique n'en fournit pas, ce qui évite toute dépendance à
 * {@link FabriqueSupport} tout en restant un état parfaitement valide.
 */
public class FabriqueFiliere implements FabriqueEntiteEtablissement<Filiere> {

    @Override
    public Class<Filiere> typeEntite() {
        return Filiere.class;
    }

    @Override
    public Filiere creer(UUID etablissementId) {
        String suffixe = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new Filiere(null, "ISOLATION-FILIERE-" + suffixe, null, null);
    }
}
