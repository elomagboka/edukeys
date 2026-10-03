package tg.novadigital.edukeys.identite.web;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * Désérialiseur qui n'accepte qu'une chaîne JSON : un nombre ou un booléen
 * (que Jackson convertirait sinon silencieusement en {@code String}) est
 * refusé, ce qui se traduit en 400 {@code CORPS_ILLISIBLE}. Utilisé sur
 * {@link LoginRequestDto#identifiant()} pour que la clé de limitation de
 * débit par compte (qui ne lit que les chaînes, {@code FiltreLimitationDebit})
 * et la valeur réellement authentifiée ne puissent jamais diverger.
 */
public class ChaineTextuelleStricte extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext contexte) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return (String) contexte.handleUnexpectedToken(String.class, parser);
        }
        return parser.getText();
    }
}
