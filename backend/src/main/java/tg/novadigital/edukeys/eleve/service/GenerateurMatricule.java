package tg.novadigital.edukeys.eleve.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.securite.CodeEtablissementFormat;
import tg.novadigital.edukeys.eleve.domain.CompteurMatricule;
import tg.novadigital.edukeys.eleve.repository.CompteurMatriculeRepository;

/**
 * Génère les matricules {@code <CODE>-<ANNEE>-<SEQUENCE>} (US-08), par exemple {@code CSJ-2026-00147}.
 * {@code ANNEE} est l'année de DÉBUT de l'année scolaire de la classe ; la séquence (5 chiffres) est
 * propre à (établissement, année) et jamais rebouclée.
 *
 * <p>Même motif que {@code GenerateurReferenceAdmission} (US-06) : exécuté dans la transaction de
 * l'inscription ({@code MANDATORY}), l'incrément se fait sous verrou pessimiste dans CETTE transaction —
 * tout échec ultérieur (homonyme, compte, flush) annule l'incrément, aucun numéro n'est consommé, aucun
 * trou. La ligne du compteur doit déjà exister : {@link CreateurLigneCompteurMatricule} la crée à zéro
 * dans une transaction à part, jamais ici.</p>
 *
 * <p>Le verrou du compteur est tenu jusqu'à la fin de la transaction, bcrypt du compte compris
 * (~200 ms) : acceptable, mais ne PAS remonter la génération du matricule plus tôt dans l'inscription.</p>
 */
@Service
public class GenerateurMatricule {

    /** Dernier numéro attribuable : le matricule porte 5 chiffres, jamais de rebouclage (aligné sur le CHECK de {@code compteurs_matricule}). */
    public static final long SEQUENCE_MAXIMALE = 99_999L;

    private final CompteurMatriculeRepository compteurMatriculeRepository;

    public GenerateurMatricule(CompteurMatriculeRepository compteurMatriculeRepository) {
        this.compteurMatriculeRepository = compteurMatriculeRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String generer(UUID etablissementId, String codeEtablissement, int anneeDebut) {
        // Défense en profondeur : un code hors format (tiret, accent, espace) donnerait un matricule
        // inutilisable, définitif. La validation à la création (US-08) et la contrainte V17 l'interdisent déjà.
        if (!CodeEtablissementFormat.estValide(codeEtablissement)) {
            throw new RegleMetierViolee(CodeErreur.MATRICULE_CODE_ETABLISSEMENT_INVALIDE,
                    "Le code de l'établissement n'est pas utilisable dans un matricule (2 à 10 majuscules ou chiffres).");
        }
        CompteurMatricule compteur = compteurMatriculeRepository.trouverPourVerrouiller(etablissementId, anneeDebut)
                // Ne se produit pas : l'orchestrateur garantit la ligne avant d'ouvrir la transaction. Jamais de
                // création ici (course sur la ligne absente = violation en pleine transaction, motif US-06 évité).
                .orElseThrow(() -> new ConflitException(CodeErreur.MODIFICATION_CONCURRENTE,
                        "Compteur de matricule indisponible, veuillez réessayer."));
        if (compteur.getDernier() >= SEQUENCE_MAXIMALE) {
            throw new RegleMetierViolee(CodeErreur.MATRICULE_SEQUENCE_EPUISEE,
                    "Plus aucun numéro de matricule disponible pour l'année %d.".formatted(anneeDebut));
        }
        long sequence = compteur.incrementerEtObtenir();
        compteurMatriculeRepository.save(compteur);
        return "%s-%04d-%05d".formatted(codeEtablissement, anneeDebut, sequence);
    }
}
