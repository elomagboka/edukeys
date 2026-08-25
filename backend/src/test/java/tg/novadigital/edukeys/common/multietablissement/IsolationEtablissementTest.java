package tg.novadigital.edukeys.common.multietablissement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.EntityType;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;
import tg.novadigital.edukeys.common.repository.BaseRepository;
import tg.novadigital.edukeys.etablissement.domain.Etablissement;
import tg.novadigital.edukeys.identite.domain.AffectationEtablissement;
import tg.novadigital.edukeys.identite.domain.JetonRafraichissement;
import tg.novadigital.edukeys.identite.domain.RoleCode;
import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.security.PermissionResolver;
import tg.novadigital.edukeys.identite.security.UtilisateurPrincipal;
import tg.novadigital.edukeys.testsupport.FabriqueEntiteEtablissement;
import tg.novadigital.edukeys.testsupport.FabriquesEntitesTest;

/**
 * Test d'isolation multi-établissement générique (T-05, sous-tâche 11).
 *
 * <p><b>Ce test s'écrit une fois et protège les 35 US suivantes</b>
 * (ADR-0002, « Risque principal et sa parade »). Il ne contient
 * <strong>aucune liste d'entités écrite à la main</strong> : les cas D1/D2
 * parcourent {@code entityManagerFactory.getMetamodel().getEntities()} et le
 * registre {@link FabriquesEntitesTest}, et les cas C1 à C8 s'exécutent pour
 * chaque fabrique enregistrée. Une entité métier ajoutée demain sous
 * {@code BaseEntity} au lieu d'{@code EntiteEtablissement}, ou une entité
 * {@code EntiteEtablissement} sans fabrique enregistrée, fait échouer ce
 * test — jamais la CI ne passe silencieusement à côté.</p>
 *
 * <p>Nettoyage par rollback transactionnel (CLAUDE.md, règle 4), même
 * précédent qu'{@code AuthControllerIntegrationTest} : la classe entière
 * s'exécute dans une seule transaction annulée à la fin, y compris les
 * appels {@code MockMvc} (même thread, même session Hibernate).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IsolationEtablissementTest {

    private static final UUID ETABLISSEMENT_A = FabriquesEntitesTest.ETABLISSEMENT_A;
    private static final UUID ETABLISSEMENT_B = FabriquesEntitesTest.ETABLISSEMENT_B;

    /**
     * Les quatre entités hors périmètre du filtre multi-établissement,
     * énumérées explicitement (ADR-0002, « Précision d'implémentation
     * T-05 ») : lues pendant le login, avant tout contexte. Toute autre
     * entité du metamodel qui n'étend pas {@code EntiteEtablissement} fait
     * échouer D1.
     */
    private static final Set<Class<?>> HORS_PERIMETRE = Set.of(
            Etablissement.class, Utilisateur.class, AffectationEtablissement.class, JetonRafraichissement.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PermissionResolver permissionResolver;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @AfterEach
    void refermerContexteResiduel() {
        // Filet de sécurité : si une assertion échoue au milieu d'un bloc
        // ContexteEtablissement.ouvrir(...), le try-with-resources referme
        // quand même la portée (close() s'exécute même sur exception) ; ce
        // nettoyage ne fait donc que couvrir un usage direct de
        // ContexteEtablissement.restaurer/purger dans un cas de test.
        ContexteEtablissement.courant().ifPresent(p -> ContexteEtablissement.restaurer(null));
    }

    // ------------------------------------------------------------------
    // D1 — toute entité du metamodel est filtrée ou explicitement hors périmètre
    // ------------------------------------------------------------------

    @Test
    @DisplayName("D1 - toute entite JPA etend EntiteEtablissement, sauf les quatre entites hors perimetre du login")
    void d1_touteEntiteEstFiltreeOuHorsPerimetreExplicite() {
        List<String> fautives = new ArrayList<>();
        for (EntityType<?> entityType : entityManagerFactory.getMetamodel().getEntities()) {
            Class<?> classeEntite = entityType.getJavaType();
            if (classeEntite == null) {
                continue;
            }
            if (EntiteEtablissement.class.isAssignableFrom(classeEntite)) {
                continue;
            }
            if (HORS_PERIMETRE.contains(classeEntite)) {
                continue;
            }
            fautives.add(classeEntite.getName());
        }

        assertThat(fautives)
                .withFailMessage(() -> "Entite(s) hors du dispositif multi-etablissement : " + fautives + ". "
                        + "Chaque entite metier doit etendre EntiteEtablissement (docs/adr/0002-multi-etablissement.md). "
                        + "Si cette entite est legitimement lue avant tout contexte (comme Etablissement, Utilisateur, "
                        + "AffectationEtablissement, JetonRafraichissement), ajoutez-la explicitement a la liste "
                        + "HORS_PERIMETRE d'IsolationEtablissementTest, en justifiant pourquoi dans un commentaire.")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // D2 — toute entité filtrée a une fabrique enregistrée
    // ------------------------------------------------------------------

    @Test
    @DisplayName("D2 - toute entite EntiteEtablissement du metamodel a une fabrique enregistree dans FabriquesEntitesTest")
    void d2_touteEntiteFiltreeAUneFabriqueEnregistree() {
        Set<Class<?>> typesAvecFabrique = new HashSet<>();
        for (FabriqueEntiteEtablissement<?> fabrique : FabriquesEntitesTest.toutes()) {
            typesAvecFabrique.add(fabrique.typeEntite());
        }

        List<String> sansFabrique = new ArrayList<>();
        for (EntityType<?> entityType : entityManagerFactory.getMetamodel().getEntities()) {
            Class<?> classeEntite = entityType.getJavaType();
            if (classeEntite == null || !EntiteEtablissement.class.isAssignableFrom(classeEntite)) {
                continue;
            }
            if (!typesAvecFabrique.contains(classeEntite)) {
                sansFabrique.add(classeEntite.getName());
            }
        }

        assertThat(sansFabrique)
                .withFailMessage(() -> "Entite(s) EntiteEtablissement sans fabrique de test : " + sansFabrique + ". "
                        + "Enregistrez une FabriqueEntiteEtablissement<" + (sansFabrique.isEmpty() ? "?" : sansFabrique.get(0))
                        + "> dans tg.novadigital.edukeys.testsupport.FabriquesEntitesTest : sans elle, cette entite "
                        + "sortirait silencieusement du perimetre du test d'isolation (IsolationEtablissementTest), "
                        + "exactement le scenario que ce verrou existe pour empecher.")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // Infrastructure commune aux cas C1 à C8
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private <T> BaseRepository<T> repositoryPour(Class<T> typeEntite) {
        for (Object bean : applicationContext.getBeansOfType(BaseRepository.class).values()) {
            for (Class<?> candidate : bean.getClass().getInterfaces()) {
                ResolvableType resolvableType = ResolvableType.forClass(candidate).as(BaseRepository.class);
                if (resolvableType == ResolvableType.NONE) {
                    continue;
                }
                if (typeEntite.equals(resolvableType.getGeneric(0).resolve())) {
                    return (BaseRepository<T>) bean;
                }
            }
        }
        throw new IllegalStateException("Aucun repository BaseRepository trouve pour " + typeEntite
                + " : ajoutez une interface *Repository extends BaseRepository<" + typeEntite.getSimpleName() + ">.");
    }

    private <T extends EntiteEtablissement> T persisterDans(UUID etablissementId, FabriqueEntiteEtablissement<T> fabrique) {
        BaseRepository<T> repository = repositoryPour(fabrique.typeEntite());
        try (var portee = ContexteEtablissement.ouvrir(etablissementId)) {
            T sauvee = repository.save(fabrique.creer(etablissementId));
            entityManager.flush();
            return sauvee;
        }
    }

    /** Contourne la visibilité package-private de {@code setEtablissementId} pour simuler un objet déjà porteur d'un établissement (copie entre contextes, R4.4). */
    private static void forcerEtablissementId(EntiteEtablissement entite, UUID valeur) {
        try {
            Field champ = EntiteEtablissement.class.getDeclaredField("etablissementId");
            champ.setAccessible(true);
            champ.set(entite, valeur);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @FunctionalInterface
    private interface Verification {
        void verifier(FabriqueEntiteEtablissement<EntiteEtablissement> fabrique) throws Exception;
    }

    @SuppressWarnings("unchecked")
    private void pourChaqueFabrique(Verification verification) throws Exception {
        assertThat(FabriquesEntitesTest.toutes()).isNotEmpty();
        for (FabriqueEntiteEtablissement<?> fabrique : FabriquesEntitesTest.toutes()) {
            verification.verifier((FabriqueEntiteEtablissement<EntiteEtablissement>) fabrique);
        }
    }

    // ------------------------------------------------------------------
    // C1 — liste
    // ------------------------------------------------------------------

    @Test
    @DisplayName("C1 - un utilisateur de A ne voit dans la liste que les lignes de A")
    void c1_listeNeVoitQueLesLignesDeSonEtablissement() throws Exception {
        pourChaqueFabrique(fabrique -> {
            persisterDans(ETABLISSEMENT_A, fabrique);
            persisterDans(ETABLISSEMENT_A, fabrique);
            persisterDans(ETABLISSEMENT_B, fabrique);

            BaseRepository<?> repository = repositoryPour(fabrique.typeEntite());
            try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_A)) {
                assertThat(repository.findAll()).hasSize(2);
            }
            try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_B)) {
                assertThat(repository.findAll()).hasSize(1);
            }
        });
    }

    // ------------------------------------------------------------------
    // C2 — findById après em.clear()
    // ------------------------------------------------------------------

    @Test
    @DisplayName("C2 - findById(id de B) depuis le contexte A renvoie vide, meme apres em.clear()")
    void c2_findByIdAprèsClearNeRenvoiePasUneLigneDUnAutreEtablissement() throws Exception {
        pourChaqueFabrique(fabrique -> {
            EntiteEtablissement entiteB = persisterDans(ETABLISSEMENT_B, fabrique);
            UUID id = entiteB.getId();

            // Indispensable : sans ce clear(), le cache de premier niveau
            // renverrait l'entité déjà chargée dans CETTE session au moment de
            // sa création, sans repasser par une requête filtrée — le test
            // prouverait alors le contraire de ce qu'il croit.
            entityManager.clear();

            BaseRepository<?> repository = repositoryPour(fabrique.typeEntite());
            try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_A)) {
                assertThat(repository.findById(id)).isEmpty();
            }
            try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_B)) {
                assertThat(repository.findById(id)).isPresent();
            }
        });
    }

    // ------------------------------------------------------------------
    // C3 — EntityManager.find direct (angle mort A1)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("C3 - EntityManager.find() direct ignore le filtre (angle mort A1) ; findById via BaseRepositoryImpl ferme ce trou")
    void c3_entityManagerFindDirectIgnoreLeFiltre_maisBaseRepositoryFindByIdNon() throws Exception {
        pourChaqueFabrique(fabrique -> {
            EntiteEtablissement entiteB = persisterDans(ETABLISSEMENT_B, fabrique);
            UUID id = entiteB.getId();
            entityManager.clear();

            try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_A)) {
                // Comportement documenté (BaseRepositoryImpl, javadoc "angle mort
                // A1") : EntityManager.find() charge directement par identifiant,
                // sans passer par une requête, donc sans jamais consulter le
                // @Filter Hibernate. C'est précisément pourquoi BaseRepository
                // n'expose ni getReferenceById ni getOne, et pourquoi
                // BaseRepositoryImpl réécrit findById en Criteria : ce test
                // documente le trou que cette réécriture ferme, il ne
                // l'exploite jamais en dehors du socle common.
                Object viaFindDirect = entityManager.find(fabrique.typeEntite(), id);
                assertThat(viaFindDirect)
                        .withFailMessage("EntityManager.find() ne devrait PAS filtrer par etablissement "
                                + "(angle mort A1 documente) : s'il filtre desormais, BaseRepositoryImpl.findById "
                                + "n'a peut-etre plus besoin de sa reecriture Criteria - a reverifier avant de le simplifier.")
                        .isNotNull();

                // Le chemin réellement exposé à l'application (BaseRepository.findById,
                // réécrit en Criteria) reste, lui, filtré.
                assertThat(repositoryPour(fabrique.typeEntite()).findById(id)).isEmpty();
            }
        });
    }

    // ------------------------------------------------------------------
    // C4 — count et Specification
    // ------------------------------------------------------------------

    @Test
    @DisplayName("C4 - count() et count(Specification) ne comptent que les lignes de l'etablissement courant")
    void c4_countEtSpecificationNeComptentQueLEtablissementCourant() throws Exception {
        pourChaqueFabrique(fabrique -> {
            persisterDans(ETABLISSEMENT_A, fabrique);
            persisterDans(ETABLISSEMENT_A, fabrique);
            persisterDans(ETABLISSEMENT_B, fabrique);
            persisterDans(ETABLISSEMENT_B, fabrique);
            persisterDans(ETABLISSEMENT_B, fabrique);

            BaseRepository<EntiteEtablissement> repository = castGenerique(repositoryPour(fabrique.typeEntite()));
            Specification<EntiteEtablissement> specificationToujoursVraie = (root, query, cb) -> cb.conjunction();

            try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_A)) {
                assertThat(repository.count()).isEqualTo(2);
                assertThat(repository.count(specificationToujoursVraie)).isEqualTo(2);
            }
            try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_B)) {
                assertThat(repository.count()).isEqualTo(3);
                assertThat(repository.count(specificationToujoursVraie)).isEqualTo(3);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static BaseRepository<EntiteEtablissement> castGenerique(BaseRepository<?> repository) {
        return (BaseRepository<EntiteEtablissement>) repository;
    }

    // ------------------------------------------------------------------
    // C5 — écriture vers B depuis le contexte A
    // ------------------------------------------------------------------

    @Test
    @DisplayName("C5 - ecrire une entite deja porteuse de l'etablissement B depuis le contexte A est refuse (R4.4)")
    void c5_ecritureInterEtablissementEstRefusee() throws Exception {
        pourChaqueFabrique(fabrique -> {
            EntiteEtablissement entiteDestineeAB = fabrique.creer(ETABLISSEMENT_B);
            forcerEtablissementId(entiteDestineeAB, ETABLISSEMENT_B);

            BaseRepository<EntiteEtablissement> repository = castGenerique(repositoryPour(fabrique.typeEntite()));
            try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_A)) {
                // save() est lui-même @Transactional (SimpleJpaRepository) : le
                // conteneur Spring traduit l'IllegalStateException levée par
                // RemplisseurEtablissement (via HibernateJpaDialect) en
                // InvalidDataAccessApiUsageException. La cause d'origine reste
                // EcritureInterEtablissementRefuseeException : c'est elle qu'on vérifie.
                assertThatThrownBy(() -> {
                    repository.save(entiteDestineeAB);
                    entityManager.flush();
                }).hasCauseInstanceOf(EcritureInterEtablissementRefuseeException.class);
            }
        });
    }

    // ------------------------------------------------------------------
    // C6 — aucun contexte ouvert
    // ------------------------------------------------------------------

    @Test
    @DisplayName("C6 - aucun contexte ouvert : la garde AOP refuse bruyamment l'appel, elle ne renvoie pas silencieusement zero ligne")
    void c6_aucunContexteOuvert_refuseBruyamment() throws Exception {
        pourChaqueFabrique(fabrique -> {
            persisterDans(ETABLISSEMENT_A, fabrique);

            assertThat(ContexteEtablissement.courant()).isEmpty();
            BaseRepository<?> repository = repositoryPour(fabrique.typeEntite());

            // Constat important, à distinguer de ce que suggère la seule lecture
            // de l'ADR-0002 §2 : le filtre Hibernate seul, armé sur la valeur de
            // secours ETABLISSEMENT_NIL, renverrait effectivement zéro ligne.
            // Mais GardeContexteEtablissement (sous-tâche 8) intercepte l'appel
            // AVANT que la requête n'atteigne Hibernate, et lève
            // ContexteEtablissementAbsentException : un repository qui renvoie
            // une liste vide est indiscernable d'un contexte oublié, et ce
            // dispositif refuse précisément cette ambiguïté (voir la javadoc de
            // GardeContexteEtablissement). C'est ce comportement observé — une
            // exception, pas une liste vide — que ce test vérifie.
            assertThatThrownBy(repository::findAll).isInstanceOf(ContexteEtablissementAbsentException.class);
        });
    }

    // ------------------------------------------------------------------
    // C7 — SUPER_ADMIN ayant basculé sur A
    // ------------------------------------------------------------------

    @Test
    @DisplayName("C7 - un SUPER_ADMIN bascule sur A n'atteint aucune donnee metier : aucune permission metier ne le porte (CLAUDE.md, regle 11)")
    void c7_superAdminBasculeSurA_neAtteintAucuneDonneeMetier() throws Exception {
        // Un role métier ordinaire portant la permission requise accède normalement.
        appellerEndpointGardeParPermission(ETABLISSEMENT_A, RoleCode.ENSEIGNANT)
                .andExpect(status().isOk());

        // SUPER_ADMIN, même après bascule sur A (le même etablissementId), ne
        // porte aucune permission métier (RoleCode.SUPER_ADMIN : ETABLISSEMENT_GERER
        // et UTILISATEUR_GERER_PLATEFORME uniquement, deux permissions de
        // plateforme) : @PreAuthorize refuse avant même d'atteindre le repository.
        appellerEndpointGardeParPermission(ETABLISSEMENT_A, RoleCode.SUPER_ADMIN)
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions appellerEndpointGardeParPermission(UUID etablissementId, RoleCode role) throws Exception {
        List<GrantedAuthority> autorites = new ArrayList<>();
        autorites.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        permissionResolver.resoudrePermissions(Set.of(role.name()))
                .forEach(permission -> autorites.add(new SimpleGrantedAuthority(permission)));

        var principal = new UtilisateurPrincipal(UUID.randomUUID(), etablissementId, Set.of(role.name()));
        var authentification = new UsernamePasswordAuthenticationToken(principal, null, autorites);

        return mockMvc.perform(get("/internal/isolation/demo-entites/nombre").with(authentication(authentification)));
    }

    // ------------------------------------------------------------------
    // C8 — modification d'une entité de B depuis le contexte A
    // ------------------------------------------------------------------

    /**
     * <b>R4.4 s'applique désormais aussi à la mise à jour</b>
     * ({@code RemplisseurEtablissement.verifierAvantMiseAJour}, hook
     * {@code @PreUpdate}). Une entité déjà persistée (identifiant non nul)
     * que Spring Data {@code save()} route vers {@code EntityManager.merge()}
     * — jamais {@code persist()} — passait auparavant sans aucun contrôle
     * (T-05, sous-tâche 11, constat initial). Ce test exige désormais le
     * refus, exactement comme C5 pour {@code persist()} : modifier un champ
     * d'une entité de B depuis le contexte A doit lever
     * {@link EcritureInterEtablissementRefuseeException}, et la ligne en base
     * ne doit porter aucune trace de la modification tentée.
     */
    @Test
    @DisplayName("C8 - modifier un champ d'une entite de B via save() (merge) depuis le contexte A est refuse (R4.4)")
    void c8_modificationDUneEntiteDeBDepuisLeContexteAEstRefusee() throws Exception {
        pourChaqueFabrique(fabrique -> {
            EntiteEtablissement entiteB = persisterDans(ETABLISSEMENT_B, fabrique);
            UUID id = entiteB.getId();
            entityManager.clear();

            String tableName = nomTable(fabrique.typeEntite());
            Field champMutable = premierChampStringDeclare(fabrique.typeEntite());
            String colonneMutee = champMutable.getName();

            try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_A)) {
                // Chargement direct par identifiant (angle mort A1, cf. C3) : seul
                // moyen d'obtenir, depuis le contexte A, une copie de l'entité de
                // B pour tenter de la ré-enregistrer — reproduit le scénario d'un
                // objet obtenu ailleurs (export, cache applicatif...) puis repassé
                // à save() dans ce contexte.
                Object copie = entityManager.find(fabrique.typeEntite(), id);
                assertThat(copie).isNotNull();
                entityManager.detach(copie);

                champMutable.setAccessible(true);
                champMutable.set(copie, "MUTEE-DEPUIS-CONTEXTE-A");

                BaseRepository<Object> repository = castGeneriqueObjet(repositoryPour(fabrique.typeEntite()));
                // save() sur une entité déjà identifiée route vers merge(), qui ne
                // déclenche le hook @PreUpdate qu'au flush (Hibernate diffère la
                // synchronisation) : contrairement à C5, ce flush() est appelé
                // directement sur l'EntityManager injecté, hors de la frontière
                // @Transactional de SimpleJpaRepository.save() — l'exception levée
                // par RemplisseurEtablissement remonte donc telle quelle, sans
                // être enveloppée par la traduction d'exceptions de Spring Data.
                assertThatThrownBy(() -> {
                    repository.save(copie);
                    entityManager.flush();
                }).isInstanceOf(EcritureInterEtablissementRefuseeException.class);
            }

            entityManager.clear();
            Integer nombreDeLignesMutees = jdbcTemplate.queryForObject(
                    "select count(*) from " + tableName + " where id = ? and " + colonneMutee + " = ?",
                    Integer.class, id, "MUTEE-DEPUIS-CONTEXTE-A");
            assertThat(nombreDeLignesMutees)
                    .withFailMessage("La modification tentee depuis le contexte A ne doit laisser aucune trace "
                            + "en base sur la ligne de B (R4.4).")
                    .isZero();
        });
    }

    private String nomTable(Class<?> typeEntite) {
        jakarta.persistence.Table annotation = typeEntite.getAnnotation(jakarta.persistence.Table.class);
        return annotation != null ? annotation.name() : typeEntite.getSimpleName().toLowerCase();
    }

    private static Field premierChampStringDeclare(Class<?> typeEntite) {
        for (Field champ : typeEntite.getDeclaredFields()) {
            if (champ.getType().equals(String.class)) {
                return champ;
            }
        }
        throw new IllegalStateException("Aucun champ String declare directement sur " + typeEntite
                + " : adapter c8_modificationDUneEntiteDeBDepuisLeContexteAEstRefusee pour cette entite.");
    }

    @SuppressWarnings("unchecked")
    private static BaseRepository<Object> castGeneriqueObjet(BaseRepository<?> repository) {
        return (BaseRepository<Object>) repository;
    }

    // ------------------------------------------------------------------
    // (a) Garde AOP : preuve bout-en-bout en contexte Spring réel
    // ------------------------------------------------------------------

    @Test
    @DisplayName("(a) GardeContexteEtablissement est reellement tissee sur les repositories : preuve en contexte Spring reel")
    void a_gardeAopEstTisseeEnConditionsReelles() throws Exception {
        pourChaqueFabrique(fabrique -> {
            assertThat(ContexteEtablissement.courant()).isEmpty();
            BaseRepository<?> repository = repositoryPour(fabrique.typeEntite());

            // Une garde AOP dont le proxy n'est pas tissé échoue silencieusement :
            // elle laisse tout passer, donnant l'impression d'être là. Seul un
            // appel réel sur un bean Spring (pas un appel direct sur l'aspect,
            // comme GardeContexteEtablissementTest) le détecte.
            assertThatThrownBy(repository::findAll).isInstanceOf(ContexteEtablissementAbsentException.class);
            assertThatThrownBy(() -> repository.count()).isInstanceOf(ContexteEtablissementAbsentException.class);
        });
    }

    // ------------------------------------------------------------------
    // (b) Aucune méthode de suppression atteignable
    // ------------------------------------------------------------------

    @Test
    @DisplayName("(b) aucune interface de repository ne contourne BaseRepository, et aucune n'expose de methode de suppression")
    void b_aucuneMethodeDeSuppressionNestAtteignable() {
        List<String> violationsHorsBaseRepository = new ArrayList<>();
        List<String> violationsStatiques = new ArrayList<>();
        List<String> violationsRuntime = new ArrayList<>();

        // Repository generique de Spring Data, pas BaseRepository : un
        // repository qui ecrit "extends JpaRepository" au lieu de
        // "extends BaseRepository" est toujours un bean Repository, donc
        // toujours decouvert ici - contrairement a getBeansOfType(BaseRepository.class),
        // qui l'aurait silencieusement laisse passer.
        var tousLesRepositories =
                applicationContext.getBeansOfType(org.springframework.data.repository.Repository.class);

        for (var entry : tousLesRepositories.entrySet()) {
            Object bean = entry.getValue();

            for (Class<?> candidate : bean.getClass().getInterfaces()) {
                if (!org.springframework.data.repository.Repository.class.isAssignableFrom(candidate)
                        || candidate == org.springframework.data.repository.Repository.class) {
                    continue;
                }
                if (!BaseRepository.class.isAssignableFrom(candidate)) {
                    violationsHorsBaseRepository.add(candidate.getName());
                    continue;
                }
                if (candidate == BaseRepository.class) {
                    continue;
                }
                // Vérification statique : toutes les méthodes atteignables sur
                // l'interface repository elle-même (héritage compris).
                for (Method methode : candidate.getMethods()) {
                    if (estUneMethodeDeSuppression(methode.getName())) {
                        violationsStatiques.add(candidate.getName() + "#" + methode.getName());
                    }
                }
            }

            // Vérification à l'exécution : ce que le proxy réellement injecté
            // expose - un proxy JDK dynamique n'implémente que les interfaces
            // déclarées, mais on le revérifie plutôt que de le supposer.
            for (Method methode : bean.getClass().getMethods()) {
                if (estUneMethodeDeSuppression(methode.getName())) {
                    violationsRuntime.add(entry.getKey() + "#" + methode.getName());
                }
            }
        }

        assertThat(violationsHorsBaseRepository)
                .withFailMessage(() -> "Interface(s) de repository qui n'etendent pas BaseRepository : "
                        + violationsHorsBaseRepository + ". CLAUDE.md, regle 4 : tout repository metier doit "
                        + "etendre BaseRepository (tg.novadigital.edukeys.common.repository.BaseRepository), "
                        + "jamais JpaRepository, JpaSpecificationExecutor ou CrudRepository directement - ces "
                        + "interfaces exposent des methodes de suppression physique et de suppression en masse "
                        + "par Specification qui court-circuitent Envers et le filtre multi-etablissement.")
                .isEmpty();
        assertThat(violationsStatiques)
                .withFailMessage(() -> "Methode(s) de suppression atteignable(s) statiquement sur une interface "
                        + "repository metier : " + violationsStatiques + ". CLAUDE.md, regle 4 : la suppression "
                        + "physique est interdite, seule la desactivation logique (EntiteDesactivable) est permise. "
                        + "Cette interface etend probablement JpaRepository ou JpaSpecificationExecutor au lieu de "
                        + "BaseRepository seul.")
                .isEmpty();
        assertThat(violationsRuntime)
                .withFailMessage(() -> "Methode(s) de suppression atteignable(s) sur le proxy Spring Data reellement "
                        + "injecte : " + violationsRuntime + ".")
                .isEmpty();
    }

    private static boolean estUneMethodeDeSuppression(String nomMethode) {
        return nomMethode.equals("delete")
                || nomMethode.equals("deleteAll")
                || nomMethode.equals("deleteById")
                || nomMethode.equals("deleteAllById")
                || nomMethode.equals("deleteAllInBatch")
                || nomMethode.equals("deleteInBatch")
                || nomMethode.startsWith("deleteBy");
    }
}
