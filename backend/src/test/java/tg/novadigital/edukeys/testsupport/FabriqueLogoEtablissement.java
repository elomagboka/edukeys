package tg.novadigital.edukeys.testsupport;

import java.util.UUID;

import tg.novadigital.edukeys.etablissement.domain.LogoEtablissement;

/**
 * Fabrique de test de {@link LogoEtablissement}, enregistrée dans
 * {@link FabriquesEntitesTest} (US-00, T-10) — même raison que
 * {@link FabriqueSite} : sans elle, l'entité échapperait silencieusement au
 * périmètre d'{@code IsolationEtablissementTest}.
 */
public class FabriqueLogoEtablissement implements FabriqueEntiteEtablissement<LogoEtablissement> {

    @Override
    public Class<LogoEtablissement> typeEntite() {
        return LogoEtablissement.class;
    }

    @Override
    public LogoEtablissement creer(UUID etablissementId) {
        // Transitoire, etablissementId nul (même convention que FabriqueSite) :
        // c'est RemplisseurEtablissement qui le remplit depuis le contexte ouvert
        // au moment de la persistance, jamais la fabrique elle-même.
        byte[] contenu = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        LogoEtablissement logo = new LogoEtablissement(null, "logo.png", "image/png", contenu.length, "empreinte-test", contenu);
        // R12 : un seul logo ACTIF par établissement (index partiel) — le test
        // d'isolation générique persiste plusieurs instances de la même fabrique
        // dans un même établissement (C1, C4). Désactivé dès la création pour ne
        // jamais entrer en conflit avec cette contrainte métier, sans fausser le
        // comptage générique (aucun cas C1-C10 ne filtre sur `actif`).
        logo.desactiver();
        return logo;
    }
}
