package cm.civica.backend.controller;

import cm.civica.backend.dto.AgentChargeDto;
import cm.civica.backend.dto.DashboardResponsableDto;
import cm.civica.backend.dto.DecisionDossierRequest;
import cm.civica.backend.dto.DossierResponseDto;
import cm.civica.backend.dto.ReassignationDossierRequest;
import cm.civica.backend.service.ResponsableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/responsable")
@Tag(name = "5. Espace Responsable (Supervision & Validation)", description = "Module de supervision administrative, tableaux de bord de KPIs, gestion de la charge des agents et validation hiérarchique")
@PreAuthorize("hasAnyAuthority('ROLE_RESPONSABLE', 'ROLE_SUPER_ADMIN')")
public class ResponsableController {

    @Autowired
    private ResponsableService responsableService;

    @Operation(summary = "Tableau de bord de supervision (KPIs)",
            description = "Retourne les indicateurs de performance clés du service administratif : volume total, dossiers en attente, taux de validation/rejet, délai moyen et dossiers en souffrance.")
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponsableDto> getDashboard() {
        return ResponseEntity.ok(responsableService.getDashboard());
    }

    @Operation(summary = "Charge de travail des agents du service",
            description = "Liste les agents affectés au service administratif ainsi que le nombre de dossiers en cours d'instruction pour chacun.")
    @GetMapping("/agents")
    public ResponseEntity<List<AgentChargeDto>> getAgentsCharge(@RequestParam(required = false) Long serviceId) {
        return ResponseEntity.ok(responsableService.getAgentsCharge(serviceId));
    }

    @Operation(summary = "Réassignation d'un dossier à un agent instructeur",
            description = "Permet au responsable d'assigner ou de réassigner l'instruction d'un dossier à un agent spécifique.")
    @PutMapping("/dossiers/{numeroSuivi}/reassigner")
    public ResponseEntity<DossierResponseDto> reassignerDossier(
            @PathVariable String numeroSuivi,
            @Valid @RequestBody ReassignationDossierRequest request) {
        return ResponseEntity.ok(responsableService.reassignerDossier(numeroSuivi, request));
    }

    @Operation(summary = "Validation hiérarchique de second niveau",
            description = "Permet au Responsable de statuer directement (approbation ou rejet motivé) sur un dossier.")
    @PutMapping("/dossiers/{numeroSuivi}/decision-hierarchique")
    public ResponseEntity<DossierResponseDto> validerHierarchique(
            @PathVariable String numeroSuivi,
            @Valid @RequestBody DecisionDossierRequest request) {
        return ResponseEntity.ok(responsableService.validerHierarchique(numeroSuivi, request));
    }
}
