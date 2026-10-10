package tg.novadigital.edukeys.eleve.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.eleve.domain.CompteurMatricule;
import tg.novadigital.edukeys.eleve.repository.CompteurMatriculeRepository;

class GenerateurMatriculeTest {

    private static final UUID ETABLISSEMENT = UUID.randomUUID();

    private CompteurMatriculeRepository repository;
    private GenerateurMatricule generateur;

    @BeforeEach
    void configurer() {
        repository = mock(CompteurMatriculeRepository.class);
        generateur = new GenerateurMatricule(repository);
    }

    private static CompteurMatricule compteurA(long dernier) throws Exception {
        CompteurMatricule compteur = new CompteurMatricule(ETABLISSEMENT, 2026);
        Field champ = CompteurMatricule.class.getDeclaredField("dernier");
        champ.setAccessible(true);
        champ.setLong(compteur, dernier);
        return compteur;
    }

    @Test
    void genere_codeAnneeEtSequenceSurCinqChiffres_etIncrementeLeCompteur() throws Exception {
        CompteurMatricule compteur = compteurA(146);
        when(repository.trouverPourVerrouiller(ETABLISSEMENT, 2026)).thenReturn(Optional.of(compteur));

        String matricule = generateur.generer(ETABLISSEMENT, "CSJ", 2026);

        assertThat(matricule).isEqualTo("CSJ-2026-00147");
        assertThat(compteur.getDernier()).isEqualTo(147);
        verify(repository).save(compteur);
    }

    @Test
    void premierNumero_estZeroZeroZeroZeroUn() throws Exception {
        when(repository.trouverPourVerrouiller(ETABLISSEMENT, 2026)).thenReturn(Optional.of(compteurA(0)));

        assertThat(generateur.generer(ETABLISSEMENT, "ESN", 2026)).isEqualTo("ESN-2026-00001");
    }

    @Test
    void dernierNumeroDisponible_estAttribue_puisLaSequenceEstEpuisee_sansRebouclage() throws Exception {
        CompteurMatricule compteur = compteurA(99_998);
        when(repository.trouverPourVerrouiller(ETABLISSEMENT, 2026)).thenReturn(Optional.of(compteur));

        assertThat(generateur.generer(ETABLISSEMENT, "CSJ", 2026)).isEqualTo("CSJ-2026-99999");

        assertThatThrownBy(() -> generateur.generer(ETABLISSEMENT, "CSJ", 2026))
                .isInstanceOfSatisfying(RegleMetierViolee.class,
                        e -> assertThat(e.getCode()).isEqualTo(CodeErreur.MATRICULE_SEQUENCE_EPUISEE));
        assertThat(compteur.getDernier()).as("pas de rebouclage, aucun incrément après l'épuisement").isEqualTo(99_999);
    }

    @Test
    void refuseUnCodeHorsFormat_plutotQuEmettreUnMatriculeInutilisable() {
        for (String code : new String[] {"CS-J", "csj", "CSJ ", "ÉCOLE", "A", "ABCDEFGHIJK", "", null}) {
            assertThatThrownBy(() -> generateur.generer(ETABLISSEMENT, code, 2026))
                    .as("code %s", code)
                    .isInstanceOfSatisfying(RegleMetierViolee.class,
                            e -> assertThat(e.getCode()).isEqualTo(CodeErreur.MATRICULE_CODE_ETABLISSEMENT_INVALIDE));
        }
        verify(repository, never()).trouverPourVerrouiller(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void ligneDeCompteurAbsente_estUnConflitARejouer_jamaisUneCreationEnPleineTransaction() {
        when(repository.trouverPourVerrouiller(ETABLISSEMENT, 2026)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> generateur.generer(ETABLISSEMENT, "CSJ", 2026))
                .isInstanceOf(ConflitException.class);
        verify(repository, never()).save(any());
    }
}
