package tg.novadigital.edukeys.testsupport;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.eleve.domain.Eleve;
import tg.novadigital.edukeys.identite.domain.Utilisateur;

/**
 * Fabrique de test de {@link Eleve} (US-08). {@code utilisateur_id} porte une FK NOT NULL vers
 * {@code utilisateurs} : un vrai compte est persisté au préalable. Matricule unique à chaque appel
 * (unicité absolue par établissement) ; {@code demandeAdmissionId} nul (lien facultatif).
 */
public class FabriqueEleve implements FabriqueEntiteEtablissement<Eleve> {

    @Override
    public Class<Eleve> typeEntite() {
        return Eleve.class;
    }

    @Override
    public Eleve creer(UUID etablissementId) {
        EntityManager entityManager = FabriqueSupport.entityManager();
        String suffixe = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        Utilisateur compte = new Utilisateur(null, "iso-eleve-" + suffixe.toLowerCase(), "hash-factice", "Eleve Isolation " + suffixe, false);
        entityManager.persist(compte);
        entityManager.flush();

        return new Eleve(null, "ISO-2000-" + suffixe, "Nom" + suffixe, "Prenoms" + suffixe, LocalDate.of(2012, 3, 4),
                "Lomé", "F", "TG", null, compte.getId(), null);
    }
}
