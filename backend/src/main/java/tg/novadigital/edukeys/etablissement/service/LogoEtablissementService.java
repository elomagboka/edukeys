package tg.novadigital.edukeys.etablissement.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.common.exception.FichierTropVolumineuxException;
import tg.novadigital.edukeys.common.exception.FormatFichierNonSupporteException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;
import tg.novadigital.edukeys.etablissement.domain.LogoEtablissement;
import tg.novadigital.edukeys.etablissement.repository.EtablissementRepository;
import tg.novadigital.edukeys.etablissement.repository.LogoEtablissementRepository;

/**
 * Gestion du logo d'un établissement (US-00). Le type de fichier est
 * déterminé par inspection des <b>magic bytes</b>, jamais par le
 * {@code Content-Type} déclaré (falsifiable) : {@code image/svg+xml} n'est
 * volontairement pas accepté (risque XSS/XXE, un SVG pouvant embarquer du
 * script ou des entités externes).
 */
@Service
public class LogoEtablissementService {

    /**
     * Lue depuis {@code spring.servlet.multipart.max-file-size}
     * (application.yml), jamais dupliquée en dur : les deux valeurs
     * couplées à la main dans deux fichiers indépendants finissaient par
     * diverger silencieusement (durcissement post-revue T-10). Sans la
     * borne Tomcat côté serveur, un dépassement renvoie de toute façon une
     * 500 brute avant d'atteindre ce service — cette constante reste donc le
     * second filet, appliqué une fois le fichier effectivement reçu.
     */
    private final long tailleMaxOctets;

    private final LogoEtablissementRepository logoEtablissementRepository;
    private final EtablissementRepository etablissementRepository;
    private final EntityManager entityManager;

    public LogoEtablissementService(
            LogoEtablissementRepository logoEtablissementRepository,
            EtablissementRepository etablissementRepository,
            EntityManager entityManager,
            @Value("${spring.servlet.multipart.max-file-size}") DataSize tailleMaxFichier) {
        this.logoEtablissementRepository = logoEtablissementRepository;
        this.etablissementRepository = etablissementRepository;
        this.entityManager = entityManager;
        this.tailleMaxOctets = tailleMaxFichier.toBytes();
    }

    /** R12 : remplacement = désactivation de l'ancien logo + création d'un nouveau, jamais une mise à jour en place. */
    @Transactional
    public LogoEtablissement remplacer(UUID etablissementId, MultipartFile fichier) {
        verifierEtablissementExiste(etablissementId);

        byte[] contenu = lireContenu(fichier);
        if (contenu.length == 0) {
            throw new RegleMetierViolee("Le logo ne peut pas être vide.");
        }
        if (contenu.length > tailleMaxOctets) {
            throw new FichierTropVolumineuxException(
                    "Le logo doit peser au plus " + (tailleMaxOctets / (1024 * 1024)) + " Mo.");
        }
        String typeMime = detecterTypeMime(contenu)
                .orElseThrow(() -> new FormatFichierNonSupporteException(
                        "Format de fichier non pris en charge : seuls image/png, image/jpeg et image/webp sont acceptés."));
        String empreinte = calculerEmpreinteSha256(contenu);
        String nomFichier = nomFichierOriginal(fichier);

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            logoEtablissementRepository.findByEtablissementIdAndActifTrue(etablissementId).ifPresent(ancien -> {
                ancien.desactiver();
                logoEtablissementRepository.save(ancien);
            });

            LogoEtablissement logo = new LogoEtablissement(etablissementId, nomFichier, typeMime, contenu.length, empreinte, contenu);
            LogoEtablissement sauve = logoEtablissementRepository.save(logo);
            entityManager.flush(); // flush avant fermeture du contexte (piège T-10 A1, même raison que EtablissementService.creer).
            return sauve;
        }
    }

    public LogoEtablissement obtenir(UUID etablissementId) {
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            return logoEtablissementRepository.findByEtablissementIdAndActifTrue(etablissementId)
                    .orElseThrow(() -> new RessourceIntrouvableException("Aucun logo pour cet établissement."));
        }
    }

    @Transactional
    public void supprimer(UUID etablissementId) {
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            LogoEtablissement logo = logoEtablissementRepository.findByEtablissementIdAndActifTrue(etablissementId)
                    .orElseThrow(() -> new RessourceIntrouvableException("Aucun logo pour cet établissement."));
            logo.desactiver();
            logoEtablissementRepository.save(logo);
            entityManager.flush();
        }
    }

    private void verifierEtablissementExiste(UUID etablissementId) {
        if (!etablissementRepository.existsById(etablissementId)) {
            throw new RessourceIntrouvableException("Établissement introuvable.");
        }
    }

    private static byte[] lireContenu(MultipartFile fichier) {
        try {
            return fichier.getBytes();
        } catch (IOException e) {
            throw new RegleMetierViolee("Fichier illisible.");
        }
    }

    private static String nomFichierOriginal(MultipartFile fichier) {
        String nom = fichier.getOriginalFilename();
        return (nom == null || nom.isBlank()) ? "logo" : nom;
    }

    /** Inspection des magic bytes, jamais le {@code Content-Type} déclaré (falsifiable). */
    private static Optional<String> detecterTypeMime(byte[] contenu) {
        if (estPng(contenu)) {
            return Optional.of("image/png");
        }
        if (estJpeg(contenu)) {
            return Optional.of("image/jpeg");
        }
        if (estWebp(contenu)) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean estPng(byte[] c) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        return commenceParOctets(c, signature);
    }

    private static boolean estJpeg(byte[] c) {
        byte[] signature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        return commenceParOctets(c, signature);
    }

    private static boolean estWebp(byte[] c) {
        if (c.length < 12) {
            return false;
        }
        boolean riff = c[0] == 'R' && c[1] == 'I' && c[2] == 'F' && c[3] == 'F';
        boolean webp = c[8] == 'W' && c[9] == 'E' && c[10] == 'B' && c[11] == 'P';
        return riff && webp;
    }

    private static boolean commenceParOctets(byte[] contenu, byte[] signature) {
        if (contenu.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (contenu[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static String calculerEmpreinteSha256(byte[] contenu) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] empreinte = digest.digest(contenu);
            return HexFormat.of().formatHex(empreinte);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible dans cette JVM.", e);
        }
    }
}
