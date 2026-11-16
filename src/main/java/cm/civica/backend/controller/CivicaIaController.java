package cm.civica.backend.controller;

import cm.civica.backend.dto.AuditCompletudeDto;
import cm.civica.backend.dto.ia.*;
import cm.civica.backend.service.CivicaIaService;
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
@RequestMapping("/api/ia")
@Tag(
    name = "8. Civica IA (Gemini & Fallback Déterministe)",
    description = "Assistance par intelligence artificielle (Chapitre 8) disponible sur toutes les interfaces Web et Mobile : statut Gemini, pré-contrôle avant dépôt, reformulation pédagogique, assistant contextuel et audit ai_suggestions."
)
public class CivicaIaController {

    @Autowired
    private CivicaIaService civicaIaService;

    @Operation(summary = "Statut du moteur Civica IA (Gemini / Repli déterministe)")
    @GetMapping("/status")
    public ResponseEntity<IaStatusDto> getStatus() {
        return ResponseEntity.ok(civicaIaService.getStatus());
    }

    @Operation(summary = "Assistant conversationnel Civica IA (Web & Mobile)",
               description = "Répond aux questions des usagers, agents et responsables avec filtrage PII, contexte de page et politique de repli déterministe.")
    @PostMapping("/chat")
    public ResponseEntity<CivicaIaChatResponse> chat(@Valid @RequestBody CivicaIaChatRequest request) {
        return ResponseEntity.ok(civicaIaService.chat(request));
    }

    @Operation(summary = "Pré-contrôle IA de complétude avant dépôt",
               description = "Vérifie les intitulés des pièces préparées par le citoyen (Web/Mobile) avant soumission du dossier.")
    @PostMapping("/precheck-completude")
    public ResponseEntity<AuditCompletudeDto> precheckCompletude(
            @Valid @RequestBody PrecheckCompletudeRequest request) {
        return ResponseEntity.ok(civicaIaService.precheckCompletude(request));
    }

    @Operation(summary = "Audit de complétude d'un dossier déposé (Agent / Supervision)")
    @GetMapping("/dossiers/{numeroSuivi}/audit")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT', 'ROLE_SOUS_RESPONSABLE', 'ROLE_RESPONSABLE', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<AuditCompletudeDto> auditDossier(@PathVariable String numeroSuivi) {
        return ResponseEntity.ok(civicaIaService.auditerDossierPourStaff(numeroSuivi));
    }

    @Operation(summary = "Reformulation pédagogique d'un motif de rejet technique (Chapitre 8 §B)")
    @PostMapping("/dossiers/{numeroSuivi}/reformuler-rejet")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT', 'ROLE_SOUS_RESPONSABLE', 'ROLE_RESPONSABLE')")
    public ResponseEntity<ReformulateRejectionResponse> reformulerRejet(
            @PathVariable String numeroSuivi,
            @Valid @RequestBody(required = false) ReformulateRejectionRequest request) {
        return ResponseEntity.ok(civicaIaService.reformulerMotifRejet(numeroSuivi, request));
    }

    @Operation(summary = "Journal d'audit des suggestions IA (table ai_suggestions)")
    @GetMapping("/suggestions")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT', 'ROLE_SOUS_RESPONSABLE', 'ROLE_RESPONSABLE', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<List<AiSuggestionDto>> getRecentSuggestions() {
        return ResponseEntity.ok(civicaIaService.getRecentSuggestions());
    }

    @Operation(summary = "Enregistrer la validation/rejet humain d'une suggestion IA")
    @PostMapping("/suggestions/{id}/feedback")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT', 'ROLE_SOUS_RESPONSABLE', 'ROLE_RESPONSABLE', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<AiSuggestionDto> feedbackSuggestion(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        boolean accepted = body != null && Boolean.TRUE.equals(body.get("accepted"));
        return ResponseEntity.ok(civicaIaService.recordFeedback(id, accepted));
    }
}
