package tg.novadigital.edukeys.testsupport;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.AnneeScolaire;
import tg.novadigital.edukeys.academique.domain.PeriodeAcademique;
import tg.novadigital.edukeys.academique.domain.TypePeriode;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * Fabrique de test de {@link PeriodeAcademique} (US-05). {@code anneeScolaireId}
 * scalaire (pas de relation JPA, voir la javadoc de l'entité) : une année
 * scolaire minimale est tout de même persistée, la table portant une FK
 * {@code annees_scolaires(id)}.
 */
public class FabriquePeriodeAcademique implements FabriqueEntiteEtablissement<PeriodeAcademique> {

    @Override
    public Class<PeriodeAcademique> typeEntite() {
        return PeriodeAcademique.class;
    }

    @Override
    public PeriodeAcademique creer(UUID etablissementId) {
        EntityManager entityManager = FabriqueSupport.entityManager();
        int anneeDebut = 2000 + ThreadLocalRandom.current().nextInt(0, 1000);
        LocalDate debutAnnee = LocalDate.of(anneeDebut, 9, 1);
        LocalDate finAnnee = LocalDate.of(anneeDebut + 1, 7, 15);
        String suffixe = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        AnneeScolaire anneeScolaire = new AnneeScolaire(null, "AN-" + suffixe, debutAnnee, finAnnee);

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            entityManager.persist(anneeScolaire);
            entityManager.flush();
        }

        LocalDate dateDebut = debutAnnee.plusDays(10);
        LocalDate dateFin = dateDebut.plusDays(60);
        int ordre = ThreadLocalRandom.current().nextInt(1, 7);
        return new PeriodeAcademique(null, anneeScolaire.getId(), "ISOLATION-PERIODE-" + suffixe,
                TypePeriode.TRIMESTRE, ordre, dateDebut, dateFin);
    }
}
