package tg.novadigital.edukeys.etablissement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import tg.novadigital.edukeys.etablissement.domain.TypeEtablissement;
import tg.novadigital.edukeys.etablissement.repository.EtablissementRepository;
import tg.novadigital.edukeys.etablissement.web.CreerEtablissementRequestDto;
import tg.novadigital.edukeys.identite.service.CreateurCompteAdministrateur;

/**
 * Piège central de la règle 12 (CLAUDE.md) : {@code EtablissementService#creer}
 * ouvre une {@code PorteeEtablissement} pour créer le site principal puis le
 * compte ADMIN, et doit forcer un flush avant de la refermer. Ce test prouve
 * l'autre moitié de l'exigence — si la création du compte ADMIN échoue,
 * <b>aucun établissement ni site</b> ne doit rester en base, la transaction
 * entière doit être annulée.
 *
 * <p>{@link CreateurCompteAdministrateur} est ici remplacé par un mock qui
 * échoue systématiquement : dans le fonctionnement réel, la seule vérification
 * de conflit d'email portée par {@code UtilisateurService#creerCompteAvecRoles}
 * est bornée au nouvel établissement (qui ne peut, par construction, avoir
 * aucune affectation préexistante) — ce test isole donc la garantie
 * transactionnelle elle-même, indépendamment de la cause précise de l'échec
 * (email déjà porté, contrainte violée, etc.).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class RollbackCreationEtablissementTest {

    @Autowired
    private EtablissementService etablissementService;

    @Autowired
    private EtablissementRepository etablissementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private CreateurCompteAdministrateur createurCompteAdministrateur;

    @Test
    void aucunEtablissementNiSiteNeSubsiste_quandLaCreationDuCompteAdminEchoue() {
        when(createurCompteAdministrateur.creerAdministrateur(any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Échec simulé de création du compte administrateur"));

        String code = "RBK" + System.nanoTime() % 100000;
        CreerEtablissementRequestDto requete = new CreerEtablissementRequestDto(
                code, "Établissement Rollback", null, TypeEtablissement.COLLEGE,
                "Lomé", null, null, null, "contact.rollback." + code.toLowerCase() + "@edukeys.tg", null, null,
                "admin.rollback." + code.toLowerCase() + "@edukeys.tg", "Admin Rollback");

        assertThatThrownBy(() -> etablissementService.creer(requete)).isInstanceOf(RuntimeException.class);

        assertThat(etablissementRepository.existsByCodeIgnoreCaseAndActifTrue(code)).isFalse();
        Long nombreSites = jdbcTemplate.queryForObject(
                "select count(*) from sites where code = ?", Long.class, code.toUpperCase() + "-PRINCIPAL");
        assertThat(nombreSites).isZero();
    }
}
