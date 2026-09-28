package tg.novadigital.edukeys.admission;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * Configuration dédiée à l'admission en ligne (US-06). Volontairement séparée
 * de {@code spring.servlet.multipart.max-file-size}, que
 * {@code LogoEtablissementService} lit aussi pour un usage différent
 * (CLAUDE.md, point 8 de la spec US-06).
 */
@Component
@ConfigurationProperties(prefix = "edukeys.admission")
public class AdmissionProperties {

    /** Taille maximale d'une pièce jointe (règle 7 de la spec US-06). */
    private DataSize tailleMaxPiece = DataSize.ofMegabytes(5);

    /** Taille maximale cumulée des pièces d'une demande. */
    private DataSize tailleMaxTotalPieces = DataSize.ofMegabytes(15);

    /** Nombre maximal de pièces par demande. */
    private int nombreMaxPieces = 5;

    /** I2 : plafond d'accusés de réception (SMS) envoyés par numéro de téléphone et par jour, succès compris. */
    private int maxAccusesParTelephoneParJour = 3;

    private final Turnstile turnstile = new Turnstile();

    /**
     * Clé secrète HMAC-SHA256 appliquée au hachage de l'adresse IP soumise
     * (règle 4 de la spec US-06, revue I7) — jamais un simple sel SHA-256,
     * toujours à sens unique. {@link tg.novadigital.edukeys.admission.VerificateurCleHachageIpAdmission}
     * fait échouer le démarrage hors {@code local}/{@code test} si cette clé
     * garde sa valeur par défaut ou est absente.
     */
    private String selHachageIp = "changez-moi";

    public DataSize getTailleMaxPiece() {
        return tailleMaxPiece;
    }

    public void setTailleMaxPiece(DataSize tailleMaxPiece) {
        this.tailleMaxPiece = tailleMaxPiece;
    }

    public DataSize getTailleMaxTotalPieces() {
        return tailleMaxTotalPieces;
    }

    public void setTailleMaxTotalPieces(DataSize tailleMaxTotalPieces) {
        this.tailleMaxTotalPieces = tailleMaxTotalPieces;
    }

    public int getNombreMaxPieces() {
        return nombreMaxPieces;
    }

    public void setNombreMaxPieces(int nombreMaxPieces) {
        this.nombreMaxPieces = nombreMaxPieces;
    }

    public int getMaxAccusesParTelephoneParJour() {
        return maxAccusesParTelephoneParJour;
    }

    public void setMaxAccusesParTelephoneParJour(int maxAccusesParTelephoneParJour) {
        this.maxAccusesParTelephoneParJour = maxAccusesParTelephoneParJour;
    }

    public Turnstile getTurnstile() {
        return turnstile;
    }

    public String getSelHachageIp() {
        return selHachageIp;
    }

    public void setSelHachageIp(String selHachageIp) {
        this.selHachageIp = selHachageIp;
    }

    /**
     * Plancher de temps de réponse de la soumission publique (I4, 3e revue) :
     * égalise création, doublon et refus. 0 le désactive.
     */
    private Duration plancherTempsReponse = Duration.ofMillis(1200);

    public Duration getPlancherTempsReponse() {
        return plancherTempsReponse;
    }

    public void setPlancherTempsReponse(Duration plancherTempsReponse) {
        this.plancherTempsReponse = plancherTempsReponse;
    }

    /** Vérification serveur de Cloudflare Turnstile (règle 4 de la spec US-06) : le vrai garde-fou, avant tout traitement du formulaire. */
    public static class Turnstile {

        private String urlVerification = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

        private String cleSecrete;

        /**
         * Délais de l'appel à Cloudflare (B2, revue US-06). Sans eux, un
         * Cloudflare lent immobilise un thread Tomcat par soumission publique,
         * sans limite : ces threads sont partagés avec /auth et tout le
         * back-office. Un délai dépassé est un refus, comme toute autre
         * indisponibilité.
         */
        private Duration delaiConnexion = Duration.ofSeconds(2);

        private Duration delaiLecture = Duration.ofSeconds(3);

        public Duration getDelaiConnexion() {
            return delaiConnexion;
        }

        public void setDelaiConnexion(Duration delaiConnexion) {
            this.delaiConnexion = delaiConnexion;
        }

        public Duration getDelaiLecture() {
            return delaiLecture;
        }

        public void setDelaiLecture(Duration delaiLecture) {
            this.delaiLecture = delaiLecture;
        }

        public String getUrlVerification() {
            return urlVerification;
        }

        public void setUrlVerification(String urlVerification) {
            this.urlVerification = urlVerification;
        }

        public String getCleSecrete() {
            return cleSecrete;
        }

        public void setCleSecrete(String cleSecrete) {
            this.cleSecrete = cleSecrete;
        }
    }
}
