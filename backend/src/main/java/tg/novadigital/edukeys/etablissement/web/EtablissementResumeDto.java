package tg.novadigital.edukeys.etablissement.web;

import java.util.UUID;

import tg.novadigital.edukeys.etablissement.domain.TypeEtablissement;

/**
 * Ligne de la liste paginée des établissements. {@code nombreSites} vient
 * directement du compteur dénormalisé {@code Etablissement#nombreSitesActifs}
 * (durcissement post-revue T-10) : {@code Etablissement} échappe au filtre
 * Hibernate multi-établissement, donc une requête agrégée sur {@code Site}
 * (une {@code EntiteEtablissement} filtrée) renvoyait une valeur fausse (0)
 * pour un appelant SUPER_ADMIN sans contexte ouvert.
 */
public record EtablissementResumeDto(
        UUID id,
        String code,
        String nom,
        String sigle,
        TypeEtablissement typeEtablissement,
        String ville,
        boolean actif,
        int nombreSites) {
}
