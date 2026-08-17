package tg.novadigital.edukeys.common.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.function.Function;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Utilitaire d'export Excel générique, réutilisable par tout module métier
 * (US-12, US-24, US-35). Ne connaît aucune entité JPA : il exporte une liste
 * de DTO à partir de colonnes déclarées par l'appelant.
 */
public final class ExcelExporter {

    private ExcelExporter() {
    }

    /** Déclare une colonne d'export : son en-tête et comment extraire sa valeur d'un élément. */
    public record Colonne<T>(String entete, Function<T, ?> extracteur) {
    }

    public static <T> byte[] exporter(String nomFeuille, List<Colonne<T>> colonnes, List<T> donnees) {
        try (Workbook classeur = new XSSFWorkbook()) {
            Sheet feuille = classeur.createSheet(nomFeuille);

            Row ligneEntete = feuille.createRow(0);
            for (int colonne = 0; colonne < colonnes.size(); colonne++) {
                ligneEntete.createCell(colonne).setCellValue(colonnes.get(colonne).entete());
            }

            int indexLigne = 1;
            for (T item : donnees) {
                Row ligne = feuille.createRow(indexLigne++);
                for (int colonne = 0; colonne < colonnes.size(); colonne++) {
                    Object valeur = colonnes.get(colonne).extracteur().apply(item);
                    ecrireValeur(ligne.createCell(colonne), valeur);
                }
            }

            for (int colonne = 0; colonne < colonnes.size(); colonne++) {
                feuille.autoSizeColumn(colonne);
            }

            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            classeur.write(sortie);
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Échec de la génération du fichier Excel.", e);
        }
    }

    private static void ecrireValeur(Cell cellule, Object valeur) {
        if (valeur == null) {
            cellule.setBlank();
        } else if (valeur instanceof Number nombre) {
            cellule.setCellValue(nombre.doubleValue());
        } else if (valeur instanceof Boolean booleen) {
            cellule.setCellValue(booleen);
        } else if (valeur instanceof Temporal) {
            cellule.setCellValue(valeur.toString());
        } else {
            cellule.setCellValue(valeur.toString());
        }
    }
}
