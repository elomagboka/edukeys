package tg.novadigital.edukeys.admission.web;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import tg.novadigital.edukeys.admission.domain.DemandeAdmission;
import tg.novadigital.edukeys.admission.domain.PieceJointeAdmission;
import tg.novadigital.edukeys.admission.domain.StatutAdmission;
import tg.novadigital.edukeys.admission.mapper.DecisionAdmissionMapper;
import tg.novadigital.edukeys.admission.mapper.DemandeAdmissionMapper;
import tg.novadigital.edukeys.admission.mapper.PieceJointeAdmissionMapper;
import tg.novadigital.edukeys.admission.service.DemandeAdmissionService;

/** Gestion des dossiers d'admission côté back-office (US-06, US-07). */
@Tag(name = "Admission")
@RestController
@RequestMapping("/api/v1/demandes-admission")
public class DemandeAdmissionController {

    private final DemandeAdmissionService demandeAdmissionService;
    private final DemandeAdmissionMapper demandeAdmissionMapper;
    private final PieceJointeAdmissionMapper pieceJointeAdmissionMapper;
    private final DecisionAdmissionMapper decisionAdmissionMapper;

    public DemandeAdmissionController(
            DemandeAdmissionService demandeAdmissionService,
            DemandeAdmissionMapper demandeAdmissionMapper,
            PieceJointeAdmissionMapper pieceJointeAdmissionMapper,
            DecisionAdmissionMapper decisionAdmissionMapper) {
        this.demandeAdmissionService = demandeAdmissionService;
        this.demandeAdmissionMapper = demandeAdmissionMapper;
        this.pieceJointeAdmissionMapper = pieceJointeAdmissionMapper;
        this.decisionAdmissionMapper = decisionAdmissionMapper;
    }

    @Operation(operationId = "creerDemandeAdmission", summary = "Crée un dossier d'admission depuis le back-office (canal ADMIN)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Dossier déjà existant (idempotence)"),
                    @ApiResponse(responseCode = "201", description = "Dossier créé"),
                    @ApiResponse(responseCode = "422", description = "Données ou choix niveau/classe invalides"),
                    @ApiResponse(responseCode = "415", description = "Pièce jointe dans un format non pris en charge")
            })
    // I9 (revue) : même traitement que la soumission publique — le corps
    // multipart est décrit explicitement, et les paramètres réels de liaison
    // sont masqués, sinon SpringDoc range typesPieces parmi les paramètres
    // d'URL.
    @RequestBody(required = true, content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            schema = @Schema(implementation = SoumissionAdmissionMultipartSchema.class)))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ADMISSION_CREER')")
    public ResponseEntity<DemandeAdmissionDto> creer(
            @Parameter(hidden = true) @Valid @RequestPart("demande") DonneesDemandeAdmissionDto demande,
            @Parameter(hidden = true) @RequestPart(name = "pieces", required = false) List<MultipartFile> pieces,
            @Parameter(hidden = true) @RequestParam(name = "typesPieces", required = false) List<String> typesPieces) {
        DemandeAdmissionService.ResultatAdmin resultat =
                demandeAdmissionService.creerAdmin(demandeAdmissionMapper.versCommande(demande), pieces, typesPieces);
        DemandeAdmission sauvee = resultat.demande();
        List<PieceJointeAdmission> piecesEnregistrees = demandeAdmissionService.listerPieces(sauvee.getId());
        DemandeAdmissionDto dto = demandeAdmissionMapper.versDto(sauvee, piecesEnregistrees, pieceJointeAdmissionMapper);
        return ResponseEntity.status(resultat.nouveau() ? org.springframework.http.HttpStatus.CREATED : org.springframework.http.HttpStatus.OK).body(dto);
    }

    @Operation(operationId = "listerDemandesAdmission", summary = "Liste paginée des demandes d'admission de l'établissement courant",
            responses = @ApiResponse(responseCode = "200", description = "Demandes d'admission"))
    @GetMapping
    @PreAuthorize("hasAuthority('ADMISSION_CONSULTER')")
    public Page<DemandeAdmissionResumeDto> lister(
            @RequestParam(required = false) StatutAdmission statut,
            Pageable pageable) {
        return demandeAdmissionService.lister(statut, pageable)
                .map(d -> new DemandeAdmissionResumeDto(
                        d.id(), d.reference(), d.nom(), d.prenoms(), d.niveauLibelle(), d.classeLibelle(),
                        d.statut(), d.canal(), d.dateSoumission(), d.dateDecision()));
    }

    @Operation(operationId = "lireDemandeAdmission", summary = "Détail d'une demande d'admission",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Demande trouvée"),
                    @ApiResponse(responseCode = "404", description = "Demande introuvable")
            })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMISSION_CONSULTER')")
    public DemandeAdmissionDto obtenir(@PathVariable UUID id) {
        DemandeAdmissionService.DemandeEtPieces resultat = demandeAdmissionService.obtenirAvecPieces(id);
        return demandeAdmissionMapper.versDto(
                resultat.demande(), resultat.pieces(), resultat.decisions(), pieceJointeAdmissionMapper, decisionAdmissionMapper);
    }

    @Operation(operationId = "deciderDemandeAdmission", summary = "Décide du sort d'un dossier d'admission (accepter, refuser, liste d'attente)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Décision enregistrée"),
                    @ApiResponse(responseCode = "400", description = "Requête invalide"),
                    @ApiResponse(responseCode = "403", description = "Accès refusé"),
                    @ApiResponse(responseCode = "404", description = "Demande introuvable"),
                    @ApiResponse(responseCode = "409", description = "Conflit : version périmée ou dossier en doublon avec un autre actif"),
                    @ApiResponse(responseCode = "422", description = "Observation obligatoire manquante ou transition de statut invalide")
            })
    @PostMapping("/{id}/decisions")
    @PreAuthorize("hasAuthority('ADMISSION_DECIDER')")
    public DemandeAdmissionDto decider(@PathVariable UUID id, @Valid @org.springframework.web.bind.annotation.RequestBody DecisionAdmissionRequeteDto requete) {
        DemandeAdmission demande = demandeAdmissionService.decider(id, requete.statut(), requete.observation(), requete.version());
        DemandeAdmissionService.DemandeEtPieces resultat = demandeAdmissionService.obtenirAvecPieces(demande.getId());
        return demandeAdmissionMapper.versDto(
                resultat.demande(), resultat.pieces(), resultat.decisions(), pieceJointeAdmissionMapper, decisionAdmissionMapper);
    }

    @Operation(operationId = "telechargerPieceAdmission", summary = "Télécharge une pièce jointe d'une demande d'admission",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Flux binaire de la pièce"),
                    @ApiResponse(responseCode = "404", description = "Demande ou pièce introuvable")
            })
    @GetMapping("/{id}/pieces/{pieceId}")
    @PreAuthorize("hasAuthority('ADMISSION_CONSULTER')")
    public ResponseEntity<byte[]> telecharger(@PathVariable UUID id, @PathVariable UUID pieceId) {
        DemandeAdmissionService.PieceEtContenu resultat = demandeAdmissionService.telechargerPiece(id, pieceId);
        PieceJointeAdmission piece = resultat.piece();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(piece.getNomOriginal(), java.nio.charset.StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(piece.getTypeMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(resultat.contenu());
    }

    @Operation(operationId = "ajouterPieceAdmission", summary = "Ajoute une pièce jointe à une demande d'admission en attente",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Pièce ajoutée"),
                    @ApiResponse(responseCode = "404", description = "Demande introuvable"),
                    @ApiResponse(responseCode = "415", description = "Format non pris en charge"),
                    @ApiResponse(responseCode = "422", description = "Dossier non modifiable (hors statut EN_ATTENTE)")
            })
    @PostMapping(path = "/{id}/pieces", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ADMISSION_CREER')")
    public ResponseEntity<PieceJointeAdmissionDto> ajouterPiece(
            @PathVariable UUID id,
            @RequestParam("typePiece") String typePiece,
            @RequestParam("fichier") MultipartFile fichier) {
        PieceJointeAdmission piece = demandeAdmissionService.ajouterPiece(id, typePiece, fichier);
        return ResponseEntity.status(201).body(pieceJointeAdmissionMapper.versDto(piece));
    }

    @Operation(operationId = "desactiverPieceAdmission", summary = "Désactive (logiquement) une pièce jointe d'une demande d'admission en attente",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Pièce désactivée"),
                    @ApiResponse(responseCode = "404", description = "Demande ou pièce introuvable"),
                    @ApiResponse(responseCode = "422", description = "Dossier non modifiable (hors statut EN_ATTENTE)")
            })
    @DeleteMapping("/{id}/pieces/{pieceId}")
    @PreAuthorize("hasAuthority('ADMISSION_CREER')")
    public ResponseEntity<Void> desactiverPiece(@PathVariable UUID id, @PathVariable UUID pieceId) {
        demandeAdmissionService.desactiverPiece(id, pieceId);
        return ResponseEntity.noContent().build();
    }
}
