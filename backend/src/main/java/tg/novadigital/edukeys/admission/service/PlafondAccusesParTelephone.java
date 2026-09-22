package tg.novadigital.edukeys.admission.service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import tg.novadigital.edukeys.admission.AdmissionProperties;

/**
 * Plafond d'accusés de réception envoyés par numéro de téléphone et par jour
 * (I2, revue US-06) : compte aussi les succès, pas seulement les tentatives —
 * un dossier renvoyé plusieurs fois (idempotence, I4) ou un dossier créé
 * plusieurs fois avec le même numéro de responsable ne doit pas se traduire
 * par une rafale de SMS. Au-delà du plafond, la demande reste enregistrée
 * normalement : seul l'envoi du SMS est tu, jamais l'écriture en base.
 *
 * <p>Compteur en mémoire (même limite que {@code CompteurAttenteCroissante} :
 * ne survit pas à plusieurs instances Render, acceptable pour un plafond de
 * confort plutôt qu'une garantie de sécurité dure).</p>
 */
@Component
public class PlafondAccusesParTelephone {

    private final Cache<String, AtomicInteger> compteurParJour;
    private final AdmissionProperties proprietes;

    public PlafondAccusesParTelephone(AdmissionProperties proprietes) {
        this.proprietes = proprietes;
        this.compteurParJour = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(Duration.ofDays(1))
                .build();
    }

    /** {@code true} si l'envoi est autorisé (et compté) ; {@code false} au-delà du plafond journalier — l'appelant ne doit alors rien envoyer. */
    public boolean autoriserEtCompter(String telephone) {
        if (telephone == null || telephone.isBlank()) {
            return true;
        }
        String cle = telephone.trim() + ":" + java.time.LocalDate.now();
        AtomicInteger compteur = compteurParJour.get(cle, k -> new AtomicInteger(0));
        int valeur = compteur.incrementAndGet();
        return valeur <= proprietes.getMaxAccusesParTelephoneParJour();
    }
}
