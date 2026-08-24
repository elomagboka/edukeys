package tg.novadigital.edukeys.common.domain;

import java.util.UUID;

import jakarta.persistence.PrePersist;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissementAbsentException;
import tg.novadigital.edukeys.common.multietablissement.EcritureInterEtablissementRefuseeException;
import tg.novadigital.edukeys.common.multietablissement.PerimetreEtablissement;
import tg.novadigital.edukeys.common.securite.JournalSecurite;

/**
 * Remplit automatiquement {@code etablissementId} à la persistance de toute
 * entité étendant {@link EntiteEtablissement} (sous-tâche 7, T-05). Personne
 * n'écrit ce champ à la main — {@code EntiteEtablissement.setEtablissementId}
 * n'est appelé que par ce listener. Enregistré via
 * {@code @EntityListeners(RemplisseurEtablissement.class)} sur
 * {@link EntiteEtablissement}, en plus de {@code AuditingEntityListener}
 * hérité de {@code BaseEntity} : la JPA invoque les listeners de la
 * superclasse puis ceux de la sous-classe, les deux coexistent sans conflit.
 *
 * <p><b>Pourquoi ce listener vit dans {@code common.domain}</b> et non dans
 * {@code common.multietablissement} : c'est ce qui permet à
 * {@code EntiteEtablissement.setEtablissementId} d'être <i>package-private</i>.
 * Aucune entité métier — elles vivent toutes dans d'autres packages — ne peut
 * alors écrire ce champ, et c'est le compilateur qui l'interdit, non une
 * convention (CLAUDE.md, même esprit que {@code BaseRepository} sans
 * {@code delete}). Effet de bord bienvenu : {@code EntiteEtablissement}
 * n'importe plus rien de {@code common.multietablissement} pour son
 * {@code @EntityListeners}.</p>
 *
 * <p>Reste ouvert, volontairement : le constructeur {@code protected}
 * {@code EntiteEtablissement(UUID)}, seul moyen légitime pour une sous-classe
 * de recevoir son établissement. R4.4 le couvre à la persistance — un
 * identifiant qui ne correspond pas au contexte courant est refusé, quel que
 * soit le chemin par lequel il a été écrit.</p>
 *
 * <p>Instancié par le fournisseur JPA (constructeur sans argument), pas par
 * Spring : il n'a besoin d'aucune dépendance injectée,
 * {@link ContexteEtablissement} et {@link JournalSecurite} exposant des
 * méthodes statiques.</p>
 *
 * <p><b>Règles (R4.1 à R4.5)</b> :</p>
 * <ul>
 *   <li>R4.1 — champ nul, contexte ouvert : remplit avec l'établissement
 *       courant ;</li>
 *   <li>R4.2 — champ nul, aucun contexte : refus bruyant
 *       ({@link ContexteEtablissementAbsentException}), jamais de
 *       persistance silencieuse d'une entité orpheline ;</li>
 *   <li>R4.3 — champ déjà renseigné et égal au contexte courant : inchangé,
 *       aucune erreur (idempotence d'un {@code save} sur une entité déjà
 *       chargée) ;</li>
 *   <li>R4.4 — champ déjà renseigné et différent du contexte courant :
 *       refus, journalisé avant d'être levé — la règle la plus importante
 *       des cinq, celle qui empêche une écriture inter-établissement
 *       volontaire ou accidentelle (copie d'objet entre deux contextes) ;</li>
 *   <li>R4.5 — {@link ContexteEtablissement#ETABLISSEMENT_NIL} : ce n'est
 *       jamais un établissement valide à assigner. C'est la valeur de secours
 *       posée par {@code ArmeurFiltreEtablissement} sur une session <em>sans
 *       contexte ouvert</em> (armer le filtre à zéro ligne), jamais une
 *       valeur que {@link ContexteEtablissement#courant()} peut retourner :
 *       {@code ContexteEtablissement.ouvrir} n'interdit pas de l'y passer
 *       explicitement par erreur, donc ce listener la traite explicitement
 *       comme une absence de contexte réel (R4.2), plutôt que de la laisser
 *       filtrer comme un identifiant d'établissement ordinaire.</li>
 * </ul>
 */
public class RemplisseurEtablissement {

    @PrePersist
    public void remplir(EntiteEtablissement entite) {
        UUID etablissementCourant = etablissementCourantReel();
        UUID etablissementExistant = entite.getEtablissementId();
        String nomEntite = entite.getClass().getSimpleName();

        if (etablissementExistant == null) {
            if (etablissementCourant == null) {
                // R4.2 (et R4.5 quand le contexte ouvert porte ETABLISSEMENT_NIL).
                JournalSecurite.ecritureEtablissementRefuseeContexteAbsent(nomEntite);
                throw new ContexteEtablissementAbsentException();
            }
            entite.setEtablissementId(etablissementCourant); // R4.1
            return;
        }

        if (etablissementCourant != null && etablissementExistant.equals(etablissementCourant)) {
            return; // R4.3
        }

        // R4.4 : champ déjà renseigné, mais soit différent du contexte
        // courant, soit aucun contexte n'est ouvert pour le confirmer — dans
        // les deux cas, une écriture qu'on ne peut pas prouver légitime est
        // refusée plutôt que tolérée.
        JournalSecurite.ecritureInterEtablissementRefusee(nomEntite, etablissementCourant, etablissementExistant);
        throw new EcritureInterEtablissementRefuseeException(
                "Écriture refusée sur %s : établissement renseigné %s différent de l'établissement courant %s."
                        .formatted(nomEntite, etablissementExistant, etablissementCourant));
    }

    /** {@code null} si aucun contexte, ou si le contexte ouvert porte la valeur de secours (R4.5). */
    private static UUID etablissementCourantReel() {
        return ContexteEtablissement.courant()
                .map(PerimetreEtablissement::etablissementId)
                .filter(id -> !ContexteEtablissement.ETABLISSEMENT_NIL.equals(id))
                .orElse(null);
    }
}
