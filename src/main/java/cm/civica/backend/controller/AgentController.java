package cm.civica.backend.controller;

import cm.civica.backend.dto.AuditCompletudeDto;
import cm.civica.backend.dto.DecisionDossierRequest;
import cm.civica.backend.dto.DossierResponseDto;
import cm.civica.backend.dto.TransfertDossierRequest;
import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/dossiers")
@Tag(name = "4. Espace Agent & Responsable", description = "Instruction des dossiers administratifs, vérification des pièces, décisions, transferts et assistance IA (LLM)")
@PreAuthorize("hasAnyAuthority('ROLE_AGENT', 'ROLE_RESPONSABLE', 'ROLE_SUPER_ADMIN')")
public class AgentController {

    @Autowired
    private AgentService agentService;

    @Operation(summary = "Lister les dossiers à instruire", description = "Retourne la liste des dossiers assignés au service de l'agent. Filtrable par statut.")
    @GetMapping
    public ResponseEntity<List<DossierResponseDto>> getDossiersAInstructionner(
            @RequestParam(required = false) StatutDossier statut) {
        return ResponseEntity.ok(agentService.getDossiersAInstructionner(statut));
    }

    @Operation(summary = "Prendre en charge un dossier", description = "Fait passer le dossier au statut EN_VERIFICATION (Pattern State) et enregistre l'agent instructeur dans l'audit.")
    @PutMapping("/{numeroSuivi}/prendre-en-charge")
    public ResponseEntity<DossierResponseDto> prendreEnCharge(@PathVariable String numeroSuivi) {
        return ResponseEntity.ok(agentService.prendreEnCharge(numeroSuivi));
    }

    @Operation(summary = "Rendre une décision (Valider ou Rejeter)", description = "Approuve (VALIDE, sous réserve de succès du circuit Chain of Responsibility) ou rejette (REJETE) le dossier avec motif obligatoire.")
    @PutMapping("/{numeroSuivi}/decision")
    public ResponseEntity<DossierResponseDto> rendreDecision(
            @PathVariable String numeroSuivi,
            @Valid @RequestBody DecisionDossierRequest request) {
        return ResponseEntity.ok(agentService.rendreDecision(numeroSuivi, request));
    }

    @Operation(summary = "Transférer un dossier vers un autre service", description = "Transfère la détention du dossier vers un service administratif cible, conformément à l'invariant formel d'unicité.")
    @PutMapping("/{numeroSuivi}/transferer")
    public ResponseEntity<DossierResponseDto> transfererDossier(
            @PathVariable String numeroSuivi,
            @Valid @RequestBody TransfertDossierRequest request) {
        return ResponseEntity.ok(agentService.transfererDossier(numeroSuivi, request));
    }

    @Operation(summary = "Assistance IA : Audit de complétude du dossier (Pattern Adapter)",
            description = "Analyse automatiquement les pièces jointes fournies par rapport au référentiel légal de la démarche et calcule un score de complétude.")
    @GetMapping("/{numeroSuivi}/audit-ia")
    public ResponseEntity<AuditCompletudeDto> auditerDossierParIa(@PathVariable String numeroSuivi) {
        return ResponseEntity.ok(agentService.auditerDossierParIa(numeroSuivi));
    }

    @Operation(summary = "Assistance IA : Suggestion de motif de rejet (Pattern Adapter)",
            description = "Génère un motif de rejet professionnel, argumenté et personnalisé selon les pièces manquantes ou observations de l'agent.")
    @PostMapping("/{numeroSuivi}/suggerer-rejet")
    public ResponseEntity<Map<String, String>> suggererMotifRejet(
            @PathVariable String numeroSuivi,
            @RequestBody(required = false) Map<String, String> body) {
        String contexte = body != null ? body.get("contexte") : "";
        String suggestion = agentService.suggererMotifRejetParIa(numeroSuivi, contexte);
        return ResponseEntity.ok(Map.of(
                "numeroSuivi", numeroSuivi,
                "suggestionMotif", suggestion
        ));
    }
}
