package tg.novadigital.edukeys.identite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.identite.domain.AffectationEtablissement;
import tg.novadigital.edukeys.identite.domain.RoleCode;
import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.repository.AffectationEtablissementRepository;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * T-05, sous-tâche 13 : {@code Utilisateur} est le cas le plus exposé du
 * dispositif multi-établissement. Contrairement à {@code DemoEntite} ou toute
 * future entité métier, elle n'étend pas {@code EntiteEtablissement} — lue
 * pendant le login, avant qu'aucun contexte n'existe (ADR-0002, précision
 * d'implémentation T-05) — donc aucun filtre Hibernate ne la protège. Son
 * cloisonnement repose entièrement, et uniquement, sur la vérification
 * explicite via {@link AffectationEtablissement} dans
 * {@link UtilisateurService#obtenirDansEtablissementCourant(UUID)} et
 * {@link UtilisateurService#listerParEtablissementCourant} — aucun filet
 * automatique derrière si l'un de ces deux points est un jour contourné ou
 * mal recopié ailleurs. Ce test le prouve, par la liste et par accès direct à
 * l'identifiant, et documente explicitement l'absence de filet (dernier cas).
 *
 * <p>Deux établissements de démonstration déjà chargés par
 * {@code R__etablissement_demo.sql} (CSJ / ESN) — pas de nouveaux
 * établissements créés ici : {@code affectations_etablissement.etablissement_id}
 * porte une contrainte de clé étrangère vers {@code etablissements} depuis la
 * migration V4, donc toute affectation de test doit viser un établissement
 * réellement présent en base.</p>
 *
 * <p>Nettoyage par rollback transactionnel (même précédent qu'{@code
 * AuthControllerIntegrationTest}) : les comptes créés ici n'existent que le
 * temps du test.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
class IsolationUtilisateursTest {

    /** Établissements de démonstration fixes (R__etablissement_demo.sql), garantis présents (contrainte FK V4). */
    private static final UUID ETABLISSEMENT_A = UUID.fromString("01977000-0000-7000-9000-000000000001");
    private static final UUID ETABLISSEMENT_B = UUID.fromString("01977000-0000-7000-9000-000000000002");

    @Autowired
    private UtilisateurService utilisateurService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private AffectationEtablissementRepository affectationEtablissementRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Utilisateur creerUtilisateurAffecte(UUID etablissementId, String email) {
        Utilisateur utilisateur = new Utilisateur(email, "hash", "Compte de test isolation", false);
        utilisateurRepository.save(utilisateur);
        AffectationEtablissement affectation =
                new AffectationEtablissement(utilisateur, etablissementId, Set.of(RoleCode.ADMIN));
        affectationEtablissementRepository.save(affectation);
        entityManager.flush();
        return utilisateur;
    }

    // ------------------------------------------------------------------
    // Liste : un ADMIN de A ne voit, parmi les comptes qu'il cree ici, que ceux de A
    // ------------------------------------------------------------------

    @Test
    void listerParEtablissementCourant_neContientPasLesComptesDUnAutreEtablissement() {
        Utilisateur compteA = creerUtilisateurAffecte(ETABLISSEMENT_A, "isolation-a-" + UUID.randomUUID() + "@edukeys.tg");
        Utilisateur compteB = creerUtilisateurAffecte(ETABLISSEMENT_B, "isolation-b-" + UUID.randomUUID() + "@edukeys.tg");

        try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_A)) {
            var page = utilisateurService.listerParEtablissementCourant(PageRequest.of(0, 100));
            assertThat(page.getContent()).extracting(Utilisateur::getId).contains(compteA.getId());
            assertThat(page.getContent()).extracting(Utilisateur::getId).doesNotContain(compteB.getId());
        }
    }

    // ------------------------------------------------------------------
    // Acces direct par identifiant
    // ------------------------------------------------------------------

    @Test
    void obtenirDansEtablissementCourant_refuse_leCompteDUnAutreEtablissement() {
        Utilisateur compteB = creerUtilisateurAffecte(ETABLISSEMENT_B, "isolation-directb-" + UUID.randomUUID() + "@edukeys.tg");

        try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_A)) {
            assertThatThrownBy(() -> utilisateurService.obtenirDansEtablissementCourant(compteB.getId()))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Test
    void obtenirDansEtablissementCourant_renvoieLeCompte_quandDansLeBonEtablissement() {
        Utilisateur compteB = creerUtilisateurAffecte(ETABLISSEMENT_B, "isolation-directb2-" + UUID.randomUUID() + "@edukeys.tg");

        try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_B)) {
            assertThat(utilisateurService.obtenirDansEtablissementCourant(compteB.getId()).getId())
                    .isEqualTo(compteB.getId());
        }
    }

    // ------------------------------------------------------------------
    // Absence documentee de filet automatique : Utilisateur n'etend pas
    // EntiteEtablissement, donc aucun filtre Hibernate ne le borne. Le
    // repository brut, seul, ne protege rien - c'est UtilisateurService qui
    // porte l'integralite du cloisonnement.
    // ------------------------------------------------------------------

    @Test
    void repositoryBrut_neFiltrePas_seulUtilisateurServiceProtege() {
        Utilisateur compteB = creerUtilisateurAffecte(ETABLISSEMENT_B, "isolation-directb3-" + UUID.randomUUID() + "@edukeys.tg");
        entityManager.clear();

        try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_A)) {
            // Constat volontaire, pas une faille exploitee : UtilisateurRepository
            // (Utilisateur n'etend pas EntiteEtablissement) n'est soumis a aucun
            // filtre Hibernate, contrairement a un repository sur EntiteEtablissement
            // (voir IsolationEtablissementTest, cas C2/C3). Documente pourquoi
            // UtilisateurService#obtenirDansEtablissementCourant est le seul rempart :
            // un appel direct au repository, ailleurs dans le code, ne serait
            // protege par rien.
            assertThat(utilisateurRepository.findById(compteB.getId()))
                    .withFailMessage("UtilisateurRepository.findById() ne filtre par construction jamais par "
                            + "etablissement (Utilisateur est hors du dispositif EntiteEtablissement, ADR-0002) : "
                            + "s'il filtre desormais, ce test et sa javadoc sont perimes.")
                    .isPresent();

            assertThatThrownBy(() -> utilisateurService.obtenirDansEtablissementCourant(compteB.getId()))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }
}
