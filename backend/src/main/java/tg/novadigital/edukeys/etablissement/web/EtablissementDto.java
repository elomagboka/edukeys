package tg.novadigital.edukeys.etablissement.web;

import java.time.Instant;
import java.util.UUID;

import tg.novadigital.edukeys.etablissement.domain.TypeEtablissement;

public record EtablissementDto(
        UUID id,
        String code,
        String nom,
        String sigle,
        TypeEtablissement typeEtablissement,
        String ville,
        String quartier,
        String boitePostale,
        String adresseLigne,
        String email,
        String telephone,
        String siteWeb,
        String paysCode,
        String fuseauHoraire,
        String deviseCode,
        String langueDefaut,
        boolean referentielInitialise,
        boolean actif,
        Instant dateCreation,
        Instant dateModification) {
}
