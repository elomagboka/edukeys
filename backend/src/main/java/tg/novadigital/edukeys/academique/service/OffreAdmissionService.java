package tg.novadigital.edukeys.academique.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tg.novadigital.edukeys.academique.OffreAdmissionQuery;
import tg.novadigital.edukeys.academique.domain.AnneeScolaire;
import tg.novadigital.edukeys.academique.domain.Classe;
import tg.novadigital.edukeys.academique.domain.Niveau;
import tg.novadigital.edukeys.academique.domain.StatutAnneeScolaire;
import tg.novadigital.edukeys.academique.repository.AnneeScolaireRepository;
import tg.novadigital.edukeys.academique.repository.ClasseRepository;
import tg.novadigital.edukeys.academique.repository.NiveauRepository;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * Implémentation d'{@link OffreAdmissionQuery} (US-06) : seul point d'entrée
 * exposé au module {@code admission} (CLAUDE.md, règle 1). Appelée depuis un
 * flux public sans contexte multi-établissement déjà ouvert (le formulaire de
 * pré-inscription n'authentifie personne) : chaque méthode ouvre elle-même sa
 * {@link PorteeEtablissement} et la referme après flush éventuel (règle 12).
 */
@Service
public class OffreAdmissionService implements OffreAdmissionQuery {

    private final AnneeScolaireRepository anneeScolaireRepository;
    private final NiveauRepository niveauRepository;
    private final ClasseRepository classeRepository;

    public OffreAdmissionService(
            AnneeScolaireRepository anneeScolaireRepository,
            NiveauRepository niveauRepository,
            ClasseRepository classeRepository) {
        this.anneeScolaireRepository = anneeScolaireRepository;
        this.niveauRepository = niveauRepository;
        this.classeRepository = classeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public OffreAdmission offreOuverte(UUID etablissementId) {
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            AnneeScolaire annee = anneeCourante(etablissementId);
            if (annee == null) {
                return new OffreAdmission(null, null, List.of());
            }

            List<Niveau> niveaux = niveauRepository.findByEtablissementIdAndActifTrueOrderByCycle_RangAscRangAsc(etablissementId);
            List<Classe> classes = classeRepository.rechercher(etablissementId, annee.getId(), null, null, null, false);
            Map<UUID, List<Classe>> classesParNiveau = classes.stream()
                    .collect(Collectors.groupingBy(c -> c.getNiveau().getId()));

            List<NiveauOffre> niveauOffres = niveaux.stream()
                    .map(niveau -> new NiveauOffre(
                            niveau.getId(),
                            niveau.getLibelle(),
                            classesParNiveau.getOrDefault(niveau.getId(), List.of()).stream()
                                    .map(c -> new ClasseOffre(c.getId(), c.getLibelle()))
                                    .toList()))
                    .toList();

            return new OffreAdmission(annee.getId(), annee.getLibelle(), niveauOffres);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifierChoix(UUID etablissementId, UUID anneeId, UUID niveauId, UUID classeId) {
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            var niveau = niveauRepository.findByIdAndEtablissementIdAndActifTrue(niveauId, etablissementId).orElse(null);
            if (niveau == null) {
                return false;
            }
            if (classeId == null) {
                return true;
            }
            return classeRepository.findByIdAndEtablissementIdAndActifTrue(classeId, etablissementId)
                    .filter(classe -> classe.getNiveau().getId().equals(niveauId))
                    .filter(classe -> classe.getAnneeScolaire().getId().equals(anneeId))
                    .isPresent();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> libelles(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<UUID> liste = List.copyOf(ids);
        Map<UUID, String> resultat = new HashMap<>();
        niveauRepository.findByIdIn(liste).forEach(n -> resultat.put(n.getId(), n.getLibelle()));
        classeRepository.findByIdIn(liste).forEach(c -> resultat.put(c.getId(), c.getLibelle()));
        return resultat;
    }

    /** Année ACTIVE si elle existe, sinon la plus proche en PREPARATION (offre ouverte pour l'année suivante). */
    private AnneeScolaire anneeCourante(UUID etablissementId) {
        return anneeScolaireRepository.findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE)
                .orElseGet(() -> anneeScolaireRepository
                        .findByEtablissementIdAndActifTrueAndStatutOrderByDateDebutDesc(etablissementId, StatutAnneeScolaire.PREPARATION)
                        .stream().reduce((premier, second) -> second).orElse(null));
    }
}
