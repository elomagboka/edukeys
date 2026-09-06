package tg.novadigital.edukeys.common.securite.limitation;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Met en cache le corps de la requête pour permettre à
 * {@link FiltreLimitationDebit} de lire l'identifiant soumis (email, jeton)
 * sans consommer le flux d'entrée à la place du contrôleur — {@code
 * HttpServletRequest.getInputStream()} ne se relit pas.
 *
 * <p>Bornée en taille ({@code tailleMaxOctets}) : les DTO concernés
 * ({@code LoginRequestDto}, {@code RefreshRequestDto}) sont minuscules,
 * quelques centaines d'octets tout au plus. Au-delà de la borne, le corps
 * n'est tout simplement pas mis en cache : le contrôleur reçoit une lecture
 * tronquée, ce qui se traduit par un 400 côté {@code GestionnaireExceptionsGlobal}
 * plutôt que de laisser un appelant malveillant faire grossir la mémoire du
 * processus avec un corps arbitrairement long.</p>
 */
final class RequeteAvecCorpsMisEnCache extends HttpServletRequestWrapper {

    private final byte[] corps;

    RequeteAvecCorpsMisEnCache(HttpServletRequest request, int tailleMaxOctets) throws IOException {
        super(request);
        this.corps = lireAuPlus(request.getInputStream(), tailleMaxOctets);
    }

    /**
     * Lit au plus {@code tailleMaxOctets + 1} octets du flux — jamais
     * davantage, même si l'appelant envoie un corps arbitrairement long :
     * c'est cette borne sur la lecture elle-même, pas seulement sur le
     * résultat conservé, qui évite qu'un corps malveillant ne soit
     * intégralement chargé en mémoire avant d'être rejeté. Un corps qui
     * dépasse la borne est traité comme absent (tableau vide) : seul le
     * compteur par IP s'applique alors, la requête continue vers le
     * contrôleur qui la rejettera à sa façon (400, ou 413 si Tomcat/Spring
     * l'a déjà tronquée en amont).
     */
    private static byte[] lireAuPlus(InputStream source, int tailleMaxOctets) throws IOException {
        ByteArrayOutputStream tampon = new ByteArrayOutputStream(Math.min(tailleMaxOctets + 1, 8192));
        byte[] bloc = new byte[4096];
        int lu;
        try {
            while (tampon.size() <= tailleMaxOctets && (lu = source.read(bloc)) != -1) {
                tampon.write(bloc, 0, lu);
            }
        } catch (IOException e) {
            return new byte[0];
        }
        return tampon.size() <= tailleMaxOctets ? tampon.toByteArray() : new byte[0];
    }

    /** {@code null} si le corps était absent, illisible ou trop volumineux — l'appelant se rabat alors sur le seul compteur par IP. */
    String corpsCommeTexte() {
        return corps.length == 0 ? null : new String(corps, StandardCharsets.UTF_8);
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream flux = new ByteArrayInputStream(corps);
        return new ServletInputStream() {
            @Override
            public int read() {
                return flux.read();
            }

            @Override
            public boolean isFinished() {
                return flux.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // Lecture toujours synchrone ici : requêtes JSON minuscules, aucun besoin d'asynchronisme.
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
