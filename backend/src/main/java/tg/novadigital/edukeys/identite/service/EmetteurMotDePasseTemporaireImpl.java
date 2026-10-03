package tg.novadigital.edukeys.identite.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tg.novadigital.edukeys.identite.EmetteurMotDePasseTemporaire;

/** Implémentation du port {@link EmetteurMotDePasseTemporaire} : délègue, bornes comprises, à {@link UtilisateurService}. */
@Service
public class EmetteurMotDePasseTemporaireImpl implements EmetteurMotDePasseTemporaire {

    private final UtilisateurService utilisateurService;

    public EmetteurMotDePasseTemporaireImpl(UtilisateurService utilisateurService) {
        this.utilisateurService = utilisateurService;
    }

    @Override
    @Transactional
    public String emettreAvecExpiration(UUID utilisateurId, Instant dateExpiration) {
        return utilisateurService.emettreMotDePasseTemporaireAvecExpiration(utilisateurId, dateExpiration);
    }
}
