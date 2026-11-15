package cm.civica.backend.controller;

import cm.civica.backend.dto.DepotDossierRequest;
import cm.civica.backend.dto.DossierResponseDto;
import cm.civica.backend.dto.PieceJustificativeDto;
import cm.civica.backend.service.CitoyenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/citoyen/dossiers")
@Tag(name = "3. Espace Citoyen", description = "Dépôt de dossiers, téléversement de pièces justificatives et suivi en ligne")
public class CitoyenController {

    @Autowired
    private CitoyenService citoyenService;

    @Operation(summary = "Dépôt d'un nouveau dossier", description = "Permet à un citoyen authentifié de soumettre un nouveau dossier administratif. Attribue un numéro de suivi unique.")
    @PostMapping("/depot")
    public ResponseEntity<DossierResponseDto> deposerDossier(@Valid @RequestBody DepotDossierRequest request) {
        return ResponseEntity.ok(citoyenService.deposerDossier(request));
    }

    @Operation(summary = "Téléversement d'une pièce justificative", description = "Permet de joindre un document (PDF, JPG, PNG) à un dossier existant.")
    @PostMapping(value = "/{numeroSuivi}/pieces", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PieceJustificativeDto> uploadPiece(
            @PathVariable String numeroSuivi,
            @RequestParam("intitule") String intitule,
            @Parameter(description = "Fichier du document à joindre")
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(citoyenService.ajouterPiece(numeroSuivi, intitule, file));
    }

    @Operation(summary = "Suivi public d'un dossier", description = "Permet de consulter en temps réel l'état d'avancement d'un dossier via son numéro de suivi unique, sans connexion requise.")
    @SecurityRequirements
    @GetMapping("/suivi/{numeroSuivi}")
    public ResponseEntity<DossierResponseDto> suivreDossier(@PathVariable String numeroSuivi) {
        return ResponseEntity.ok(citoyenService.getDossierByNumeroSuivi(numeroSuivi));
    }

    @Operation(summary = "Mes dossiers", description = "Retourne la liste de tous les dossiers déposés par le citoyen actuellement connecté.")
    @GetMapping({"", "/mes-dossiers"})
    public ResponseEntity<List<DossierResponseDto>> getMesDossiers() {
        return ResponseEntity.ok(citoyenService.getMesDossiers());
    }
}

