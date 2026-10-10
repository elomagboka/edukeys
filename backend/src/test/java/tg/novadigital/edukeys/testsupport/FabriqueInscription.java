package tg.novadigital.edukeys.testsupport;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.Classe;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;
import tg.novadigital.edukeys.eleve.domain.Eleve;
import tg.novadigital.edukeys.eleve.domain.Inscription;

/**
 * Fabrique de test d'{@link Inscription} (US-08) : un élève neuf et une classe (via {@link FabriqueClasse})
 * sont persistés au préalable ; l'inscription porte l'année et le site de cette classe.
 */
public class FabriqueInscription implements FabriqueEntiteEtablissement<Inscription> {

    @Override
    public Class<Inscription> typeEntite() {
        return Inscription.class;
    }

    @Override
    public Inscription creer(UUID etablissementId) {
        EntityManager entityManager = FabriqueSupport.entityManager();
        Eleve eleve = new FabriqueEleve().creer(etablissementId);
        Classe classe = new FabriqueClasse().creer(etablissementId);

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            entityManager.persist(eleve);
            entityManager.persist(classe);
            entityManager.flush();
        }

        return new Inscription(null, eleve, classe.getAnneeScolaire().getId(), classe.getId(), classe.getSiteId(), Instant.now());
    }
}
