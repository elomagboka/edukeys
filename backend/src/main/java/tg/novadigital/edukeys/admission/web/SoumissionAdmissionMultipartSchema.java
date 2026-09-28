package tg.novadigital.edukeys.admission.web;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import tg.novadigital.edukeys.admission.domain.TypePieceAdmission;

/**
 * Documentation OpenAPI uniquement (I9, revue) : décrit les trois parties du
 * corps {@code multipart/form-data} réellement envoyé par le client
 * (soumission publique et création back-office). Jamais utilisée pour la
 * liaison Spring — {@code @RequestPart}/{@code @RequestParam} sur les
 * méthodes de contrôleur restent le mécanisme réel, qui fonctionne en
 * production (Tomcat alimente {@code request.getParameterMap()} depuis les
 * parts non-fichier d'un multipart) ; seule la doc plaçait {@code typesPieces}
 * à tort côté paramètres d'URL.
 */
@Schema(name = "SoumissionAdmissionMultipart")
public class SoumissionAdmissionMultipartSchema {

    @Schema(description = "Partie JSON du dossier (voir le schéma DonneesDemandeAdmission / SoumissionPubliqueAdmission)", requiredMode = Schema.RequiredMode.REQUIRED)
    public Object demande;

    @ArraySchema(schema = @Schema(type = "string", format = "binary", description = "Pièce jointe (PDF, JPEG ou PNG)"))
    public List<Object> pieces;

    @ArraySchema(schema = @Schema(implementation = TypePieceAdmission.class,
            description = "Type de chaque pièce, dans le même ordre que 'pieces'"))
    public List<String> typesPieces;
}
