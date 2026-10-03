package tg.novadigital.edukeys.common.securite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

/** US-08a : empreintes HMAC à clé serveur, sans repli, et normalisation unique de l'identifiant. */
class JournalSecuriteEmpreinteTest {

    private static final String CLE_VALIDE = "cle-empreinte-test-unitaire-32-octets-minimum!!";

    @Test
    void cleAbsenteOuTropCourte_faitEchouerLeDemarrage_sansValeurDeRepli() {
        assertThatThrownBy(() -> new CleEmpreinteSecurite(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CleEmpreinteSecurite("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CleEmpreinteSecurite("trop-courte")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void empreinte_estUnHmacDeterministe_quiNEstPasUnSha256SansCle() throws Exception {
        new CleEmpreinteSecurite(CLE_VALIDE);

        String empreinte = JournalSecurite.empreinte("mat-2026-0001");

        assertThat(empreinte).hasSize(16).isEqualTo(JournalSecurite.empreinte("mat-2026-0001"));
        assertThat(empreinte).isNotEqualTo(JournalSecurite.empreinte("mat-2026-0002"));
        String sha256Nu = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest("mat-2026-0001".getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        assertThat(empreinte).isNotEqualTo(sha256Nu);
    }

    @Test
    void normaliser_retireLesEspacesEtMetEnMinusculesSansLocale() {
        assertThat(IdentifiantConnexion.normaliser("  MAT-2026-0001 ")).isEqualTo("mat-2026-0001");
        assertThat(IdentifiantConnexion.normaliser("Marie@Edukeys.TG")).isEqualTo("marie@edukeys.tg");
        // Le « I » majuscule reste « i » (pas de « ı » turc) : Locale.ROOT.
        assertThat(IdentifiantConnexion.normaliser("TITI")).isEqualTo("titi");
        assertThat(IdentifiantConnexion.normaliser(null)).isNull();
    }
}
