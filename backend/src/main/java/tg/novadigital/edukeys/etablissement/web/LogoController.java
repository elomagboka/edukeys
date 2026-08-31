package tg.novadigital.edukeys.etablissement.web;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import tg.novadigital.edukeys.etablissement.domain.LogoEtablissement;
import tg.novadigital.edukeys.etablissement.mapper.LogoMapper;
import tg.novadigital.edukeys.etablissement.service.LogoEtablissementService;

/** Logo d'un établissement (US-00). */
@Tag(name = "Logo établissement")
@RestController
@RequestMapping("/api/v1/etablissements/{etablissementId}/logo")
public class LogoController {

    private final LogoEtablissementService logoEtablissementService;
    private final LogoMapper logoMapper;

    public LogoController(LogoEtablissementService logoEtablissementService, LogoMapper logoMapper) {
        this.logoEtablissementService = logoEtablissementService;
        this.logoMapper = logoMapper;
    }

    @Operation(summary = "Remplace le logo d'un établissement (l'ancien est désactivé, un nouveau créé)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Logo remplacé"),
                    @ApiResponse(responseCode = "415", description = "Format non pris en charge (magic bytes)"),
                    @ApiResponse(responseCode = "413", description = "Fichier trop volumineux (> 1 Mo)")
            })
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ETABLISSEMENT_GERER')")
    public LogoDto remplacer(@PathVariable UUID etablissementId, @RequestParam("fichier") MultipartFile fichier) {
        GardeAccesEtablissement.verifierAcces(etablissementId);
        LogoEtablissement logo = logoEtablissementService.remplacer(etablissementId, fichier);
        return logoMapper.versDto(logo);
    }

    @Operation(summary = "Télécharge le logo d'un établissement",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Flux binaire du logo", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "image/*")),
                    @ApiResponse(responseCode = "404", description = "Aucun logo pour cet établissement")
            })
    @GetMapping(produces = {MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE, "image/webp"})
    @PreAuthorize("hasAuthority('ETABLISSEMENT_GERER')")
    public ResponseEntity<byte[]> obtenir(@PathVariable UUID etablissementId) {
        GardeAccesEtablissement.verifierAcces(etablissementId);
        LogoEtablissement logo = logoEtablissementService.obtenir(etablissementId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.getTypeMime()))
                .eTag(logo.getEmpreinteSha256())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + logo.getNomFichier() + "\"")
                .body(logo.getContenu());
    }

    @Operation(summary = "Supprime (désactivation logique) le logo d'un établissement",
            responses = @ApiResponse(responseCode = "204", description = "Logo supprimé"))
    @DeleteMapping
    @PreAuthorize("hasAuthority('ETABLISSEMENT_GERER')")
    public ResponseEntity<Void> supprimer(@PathVariable UUID etablissementId) {
        GardeAccesEtablissement.verifierAcces(etablissementId);
        logoEtablissementService.supprimer(etablissementId);
        return ResponseEntity.noContent().build();
    }
}
