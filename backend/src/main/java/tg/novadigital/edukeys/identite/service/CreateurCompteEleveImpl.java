package tg.novadigital.edukeys.identite.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tg.novadigital.edukeys.identite.CreateurCompteEleve;

/** Implémentation du port {@link CreateurCompteEleve} : délègue à {@link UtilisateurService#creerCompteEleve}. */
@Service
public class CreateurCompteEleveImpl implements CreateurCompteEleve {

    private final UtilisateurService utilisateurService;

    public CreateurCompteEleveImpl(UtilisateurService utilisateurService) {
        this.utilisateurService = utilisateurService;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID creerCompteEleve(String matricule, String nomComplet) {
        return utilisateurService.creerCompteEleve(matricule, nomComplet);
    }
}
