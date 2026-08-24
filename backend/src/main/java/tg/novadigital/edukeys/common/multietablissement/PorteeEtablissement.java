package tg.novadigital.edukeys.common.multietablissement;

/**
 * Portée d'un contexte d'établissement ouvert par {@link ContexteEtablissement#ouvrir}.
 * {@code close()} restaure le périmètre précédent (jamais un simple
 * {@code remove()}) : c'est ce qui rend correcte l'imbrication de deux
 * portées, par exemple un traitement planifié qui ouvre un contexte
 * établissement A, appelle un service qui ouvre ponctuellement le contexte B,
 * puis referme B — le contexte A doit alors redevenir courant, pas
 * disparaître.
 *
 * <p>Usage : {@code try (var portee = ContexteEtablissement.ouvrir(id)) { ... }}</p>
 */
public final class PorteeEtablissement implements AutoCloseable {

    private final PerimetreEtablissement precedent;
    private boolean fermee;

    PorteeEtablissement(PerimetreEtablissement precedent) {
        this.precedent = precedent;
    }

    @Override
    public void close() {
        if (fermee) {
            return;
        }
        fermee = true;
        ContexteEtablissement.restaurer(precedent);
    }
}
