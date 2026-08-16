package tg.novadigital.edukeys.common.domain;

import java.security.SecureRandom;
import java.util.UUID;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.uuid.UuidValueGenerator;

/**
 * Générateur d'UUID version 7 (RFC 9562) : 48 bits d'horodatage en tête,
 * suivis de bits aléatoires. Ordonné dans le temps — bon pour la localité
 * d'insertion dans l'index — tout en restant imprévisible de l'extérieur.
 *
 * <p>Générateur personnalisé car la version d'Hibernate embarquée
 * (6.6.53.Final) n'expose pas encore {@code UuidGenerator.Style.VERSION_7}.
 */
public final class UuidV7Generator implements UuidValueGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public UUID generateUuid(SharedSessionContractImplementor session) {
        byte[] valeur = new byte[16];
        RANDOM.nextBytes(valeur);

        long horodatageMillis = System.currentTimeMillis();
        valeur[0] = (byte) (horodatageMillis >>> 40);
        valeur[1] = (byte) (horodatageMillis >>> 32);
        valeur[2] = (byte) (horodatageMillis >>> 24);
        valeur[3] = (byte) (horodatageMillis >>> 16);
        valeur[4] = (byte) (horodatageMillis >>> 8);
        valeur[5] = (byte) horodatageMillis;

        valeur[6] = (byte) ((valeur[6] & 0x0F) | 0x70);
        valeur[8] = (byte) ((valeur[8] & 0x3F) | 0x80);

        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (valeur[i] & 0xFF);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (valeur[i] & 0xFF);
        }
        return new UUID(msb, lsb);
    }
}
