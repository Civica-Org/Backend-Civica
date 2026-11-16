package cm.civica.backend.controller;

import cm.civica.backend.dto.AgentChargeDto;
import cm.civica.backend.dto.CreateUserAdminRequest;
import cm.civica.backend.dto.DashboardResponsableDto;
import cm.civica.backend.dto.DecisionDossierRequest;
import cm.civica.backend.dto.DossierResponseDto;
import cm.civica.backend.dto.ReassignationDossierRequest;
import cm.civica.backend.dto.ServiceAdminDto;
import cm.civica.backend.dto.UpdateUserAdminRequest;
import cm.civica.backend.dto.UtilisateurAdminDto;
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
@Tag(name = "5. Espace Supervision (Responsable & Sous-Responsable)", description = "Module de supervision administrative, tableaux de bord de KPIs, gestion de la charge, hiérarchie d'équipe et validation hiérarchique")
@PreAuthorize("hasAnyAuthority('ROLE_RESPONSABLE', 'ROLE_SOUS_RESPONSABLE')")
public class ResponsableController {

    @Autowired
    private ResponsableService responsableService;

    @Operation(summary = "Tableau de bord de supervision (KPIs)",
            description = "Retourne les indicateurs de performance clés du service (Sous-Responsable) ou de l'organisation et ses services (Responsable).")
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponsableDto> getDashboard(@RequestParam(required = false) Long serviceId) {
        return ResponseEntity.ok(responsableService.getDashboard(serviceId));
    }

    @Operation(summary = "Charge de travail de l'équipe",
            description = "Liste les agents (et sous-responsables pour le Responsable d'organisation) ainsi que le nombre de dossiers en cours pour chacun.")
    @GetMapping("/agents")
    public ResponseEntity<List<AgentChargeDto>> getAgentsCharge(@RequestParam(required = false) Long serviceId) {
        return ResponseEntity.ok(responsableService.getAgentsCharge(serviceId));
    }

    @Operation(summary = "Services du périmètre de supervision",
            description = "Retourne les services de l'organisation (Responsable) ou le service rattaché (Sous-Responsable).")
    @GetMapping("/services")
    public ResponseEntity<List<ServiceAdminDto>> getServicesPerimetre() {
        return ResponseEntity.ok(responsableService.getServicesPerimetre());
    }

    @Operation(summary = "Réassignation d'un dossier à un instructeur",
            description = "Permet au responsable ou sous-responsable d'assigner ou de réassigner l'instruction d'un dossier à un agent ou sous-responsable du service.")
    @PutMapping("/dossiers/{numeroSuivi}/reassigner")
    public ResponseEntity<DossierResponseDto> reassignerDossier(
            @PathVariable String numeroSuivi,
            @Valid @RequestBody ReassignationDossierRequest request) {
        return ResponseEntity.ok(responsableService.reassignerDossier(numeroSuivi, request));
    }

    @Operation(summary = "Validation hiérarchique de second niveau",
            description = "Permet au Responsable ou Sous-Responsable de statuer directement (approbation ou rejet motivé) sur un dossier.")
    @PutMapping("/dossiers/{numeroSuivi}/decision-hierarchique")
    public ResponseEntity<DossierResponseDto> validerHierarchique(
            @PathVariable String numeroSuivi,
            @Valid @RequestBody DecisionDossierRequest request) {
        return ResponseEntity.ok(responsableService.validerHierarchique(numeroSuivi, request));
    }

    @Operation(summary = "Lister les membres (Agents et Sous-Responsables) de l'organisation",
            description = "Réservé au Responsable d'organisation pour superviser les équipes affectées à ses services.")
    @GetMapping("/equipe")
    @PreAuthorize("hasAuthority('ROLE_RESPONSABLE')")
    public ResponseEntity<List<UtilisateurAdminDto>> getEquipeOrganisation() {
        return ResponseEntity.ok(responsableService.getEquipeOrganisation());
    }

    @Operation(summary = "Créer un agent ou sous-responsable dans un service de l'organisation",
            description = "Un service peut contenir plusieurs agents et au maximum un sous-responsable.")
    @PostMapping("/equipe")
    @PreAuthorize("hasAuthority('ROLE_RESPONSABLE')")
    public ResponseEntity<UtilisateurAdminDto> createMembreEquipe(
            @Valid @RequestBody CreateUserAdminRequest request) {
        return ResponseEntity.ok(responsableService.createMembreEquipe(request));
    }

    @Operation(summary = "Modifier l'affectation ou le statut d'un membre de l'organisation",
            description = "Permet au Responsable d'organisation de modifier le rôle (Agent/Sous-Responsable), le service ou le statut actif dans son organisation.")
    @PutMapping("/equipe/{id}")
    @PreAuthorize("hasAuthority('ROLE_RESPONSABLE')")
    public ResponseEntity<UtilisateurAdminDto> updateMembreEquipe(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserAdminRequest request) {
        return ResponseEntity.ok(responsableService.updateMembreEquipe(id, request));
    }

    @Operation(summary = "Activer / Désactiver un membre de l'organisation")
    @PatchMapping("/equipe/{id}/toggle-actif")
    @PreAuthorize("hasAuthority('ROLE_RESPONSABLE')")
    public ResponseEntity<UtilisateurAdminDto> toggleMembreEquipeActif(@PathVariable Long id) {
        return ResponseEntity.ok(responsableService.toggleMembreEquipeActif(id));
    }
}

