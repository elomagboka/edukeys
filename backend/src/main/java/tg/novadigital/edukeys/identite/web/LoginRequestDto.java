package tg.novadigital.edukeys.identite.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Identifiants de connexion Edukeys. {@code identifiant} est l'email du
 * personnel ou le matricule d'un élève (US-08a) : jamais validé comme email,
 * et obligatoirement une chaîne JSON (un nombre est refusé en 400).
 *
 * <p><strong>Alias de compatibilité {@code email}</strong> : l'ancien nom du champ
 * reste accepté pendant une version, car l'API et le site statique se déploient
 * séparément et un retour arrière se fait par redéploiement de tag
 * (ADR-0004/0007). Règle (identique dans {@code FiltreLimitationDebit}, sinon
 * l'alias contournerait la limitation par compte) : {@code identifiant} prime
 * dès qu'il est présent et non nul ; {@code email} n'est lu que s'il est absent.
 * Les deux passent par le même désérialiseur strict. Le contrat OpenAPI ne publie
 * que {@code identifiant}. À supprimer avec le filtre : issue de retrait de
 * l'alias {@code email}, #107.</p>
 */
public record LoginRequestDto(
        @NotBlank @Size(min = 1, max = 255) String identifiant,
        @NotBlank @Size(min = 1, max = 255) String motDePasse) {

    @JsonCreator
    static LoginRequestDto depuisJson(
            @JsonProperty("identifiant") @JsonDeserialize(using = ChaineTextuelleStricte.class) String identifiant,
            @JsonProperty("email") @JsonDeserialize(using = ChaineTextuelleStricte.class) @Schema(hidden = true) String emailAncienNom,
            @JsonProperty("motDePasse") String motDePasse) {
        return new LoginRequestDto(identifiant != null ? identifiant : emailAncienNom, motDePasse);
    }
}
