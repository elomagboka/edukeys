package tg.novadigital.edukeys.admission.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import tg.novadigital.edukeys.academique.OffreAdmissionQuery;
import tg.novadigital.edukeys.admission.mapper.DemandeAdmissionMapper;
import tg.novadigital.edukeys.admission.service.DemandeAdmissionService;
import tg.novadigital.edukeys.common.securite.reseau.FiltreAdresseIpCliente;

/**
 * Pré-inscription en ligne (US-06) : les deux seules routes {@code permitAll}
 * de tout le module, nommément déclarées dans {@code SecurityConfig} — jamais
 * {@code /public/**} entier (CLAUDE.md, règle 11). Protégées par
 * {@code FiltreLimitationDebit} (double compteur IP + établissement/téléphone).
 */
@Tag(name = "Admission publique")
@RestController
@RequestMapping("/api/v1/public/etablissements/{code}")
public class AdmissionPublicController {

    private final DemandeAdmissionService demandeAdmissionService;
    private final DemandeAdmissionMapper demandeAdmissionMapper;
    private final PlancherTempsReponseAdmission plancherTempsReponse;

    public AdmissionPublicController(DemandeAdmissionService demandeAdmissionService,
            DemandeAdmissionMapper demandeAdmissionMapper, PlancherTempsReponseAdmission plancherTempsReponse) {
        this.demandeAdmissionService = demandeAdmissionService;
        this.demandeAdmissionMapper = demandeAdmissionMapper;
        this.plancherTempsReponse = plancherTempsReponse;
    }

    @Operation(operationId = "lireOffreAdmissionPublique", summary = "Offre d'admission publique d'un établissement (année, niveaux, classes ouverts)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Offre d'admission"),
                    @ApiResponse(responseCode = "404", description = "Établissement introuvable"),
                    @ApiResponse(responseCode = "429", description = "Trop de tentatives ; en-tête Retry-After")
            })
    @GetMapping("/offre-admission")
    @SecurityRequirements
    public OffreAdmissionDto lireOffre(@PathVariable String code) {
        DemandeAdmissionService.OffrePublique offrePublique = demandeAdmissionService.obtenirOffrePublique(code);
        OffreAdmissionQuery.OffreAdmission offre = offrePublique.offre();
        List<OffreAdmissionDto.NiveauOffreDto> niveaux = offre.niveaux().stream()
                .map(n -> new OffreAdmissionDto.NiveauOffreDto(
                        n.id(), n.libelle(),
                        n.classes().stream().map(c -> new OffreAdmissionDto.ClasseOffreDto(c.id(), c.libelle())).toList()))
                .toList();
        return new OffreAdmissionDto(
                offre.anneeScolaireId(), offre.anneeScolaireLibelle(), offrePublique.admissionsOuvertes(),
                offrePublique.etablissementNom(), offrePublique.etablissementLogoUrl(), niveaux);
    }

    @Operation(operationId = "soumettreDemandeAdmissionPublique",
            summary = "Soumet une demande d'admission en ligne (dossier + pièces, un seul appel multipart)",
            description = "Le jeton Cloudflare Turnstile est requis dans l'en-tête CF-Turnstile-Response "
                    + "(vérifié avant toute analyse du corps multipart) — jamais dans le corps.",
            parameters = {
                    @io.swagger.v3.oas.annotations.Parameter(
                            name = "CF-Turnstile-Response", in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER,
                            required = true, description = "Jeton Cloudflare Turnstile (règle 4 de la spec US-06)")
            },
            responses = {
                    @ApiResponse(responseCode = "201",
                            description = "Accusé de réception (I4 : réponse identique, dossier nouveau ou déjà existant)"),
                    @ApiResponse(responseCode = "404", description = "Établissement introuvable"),
                    @ApiResponse(responseCode = "422", description = "Vérification anti-robot échouée, admission fermée, ou données invalides"),
                    @ApiResponse(responseCode = "415", description = "Pièce jointe dans un format non pris en charge"),
                    @ApiResponse(responseCode = "429", description = "Trop de tentatives ; en-tête Retry-After")
            })
    // I9 (revue) : @Parameter(in = DEFAULT) seul ne suffisait pas à sortir
    // typesPieces des "parameters" du contrat — SpringDoc infère le "in" d'un
    // @RequestParam avant de lire l'annotation. Un @RequestBody explicite,
    // décrivant les trois parties du multipart (voir SoumissionAdmissionMultipartSchema),
    // et @Parameter(hidden = true) sur les paramètres réels de liaison, est la
    // seule combinaison qui déplace bien typesPieces dans le schéma du corps
    // (vérifié en relisant docs/api/openapi.json régénéré).
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @io.swagger.v3.oas.annotations.media.Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = SoumissionAdmissionMultipartSchema.class)))
    @PostMapping(path = "/demandes-admission", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirements
    public ResponseEntity<AccuseReceptionAdmissionDto> soumettre(
            @PathVariable String code,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            @Valid @RequestPart("demande") SoumissionPubliqueAdmissionDto demande,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            @RequestPart(name = "pieces", required = false) List<MultipartFile> pieces,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            @RequestParam(name = "typesPieces", required = false) List<String> typesPieces,
            HttpServletRequest request) {
        // I4 (3e revue) : le plancher couvre le traitement ET les refus de
        // validation, sinon le temps de réponse trahit ce que le corps tait.
        DemandeAdmissionService.Accuse accuse = plancherTempsReponse.executerAvecPlancher(
                () -> demandeAdmissionService.soumettrePublique(
                        code, demandeAdmissionMapper.versCommande(demande.demande()), demande.siteWeb(), pieces,
                        typesPieces, FiltreAdresseIpCliente.adresseIpDe(request)));
        // I4 : toujours 201, corps limité à la référence et à un message générique —
        // que le dossier soit nouveau ou déjà existant (idempotence).
        AccuseReceptionAdmissionDto dto = new AccuseReceptionAdmissionDto(
                accuse.reference(), "Votre demande a bien été enregistrée. Conservez cette référence.");
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}
