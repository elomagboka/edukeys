package tg.novadigital.edukeys.common.fichier;

import java.util.Optional;

/**
 * Détection du type réel d'un fichier par inspection des <b>magic bytes</b>,
 * jamais par le {@code Content-Type} déclaré par l'appelant (falsifiable).
 * Factorisé ici pour être partagé entre {@code LogoEtablissementService}
 * (images) et le module {@code admission} (PDF/JPEG/PNG, US-06) — un module
 * {@code common} n'introduit aucune dépendance vers un module métier.
 */
public final class DetecteurTypeFichier {

    private DetecteurTypeFichier() {
    }

    public static boolean estPng(byte[] contenu) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        return commenceParOctets(contenu, signature);
    }

    public static boolean estJpeg(byte[] contenu) {
        byte[] signature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        return commenceParOctets(contenu, signature);
    }

    public static boolean estWebp(byte[] contenu) {
        if (contenu.length < 12) {
            return false;
        }
        boolean riff = contenu[0] == 'R' && contenu[1] == 'I' && contenu[2] == 'F' && contenu[3] == 'F';
        boolean webp = contenu[8] == 'W' && contenu[9] == 'E' && contenu[10] == 'B' && contenu[11] == 'P';
        return riff && webp;
    }

    public static boolean estPdf(byte[] contenu) {
        byte[] signature = {'%', 'P', 'D', 'F', '-'};
        return commenceParOctets(contenu, signature);
    }

    /** Détection restreinte aux formats acceptés par l'admission en ligne (US-06) : PDF, JPEG, PNG — jamais SVG ni Office. */
    public static Optional<String> detecterTypeMimePieceAdmission(byte[] contenu) {
        if (estPdf(contenu)) {
            return Optional.of("application/pdf");
        }
        if (estJpeg(contenu)) {
            return Optional.of("image/jpeg");
        }
        if (estPng(contenu)) {
            return Optional.of("image/png");
        }
        return Optional.empty();
    }

    public static boolean commenceParOctets(byte[] contenu, byte[] signature) {
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
}
