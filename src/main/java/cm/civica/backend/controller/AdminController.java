package cm.civica.backend.controller;

import cm.civica.backend.dto.*;
import cm.civica.backend.model.Demarche;
import cm.civica.backend.model.Organisation;
import cm.civica.backend.model.OrganisationType;
import cm.civica.backend.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "6. Administration Plateforme", description = "Gestion globale du paramétrage institutionnel CIVICA (organisations, services, démarches, utilisateurs)")
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Operation(summary = "Statistiques globales de la plateforme", description = "Retourne la synthèse globale pour le tableau de bord Administrateur Principal.")
    @GetMapping("/stats")
    public ResponseEntity<AdminDashboardStatsDto> getDashboardStats() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    @Operation(summary = "Lister tous les utilisateurs de la plateforme")
    @GetMapping("/users")
    public ResponseEntity<List<UtilisateurAdminDto>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @Operation(
            summary = "Rechercher un compte CIVICA par email",
            description = "Permet à l'administrateur de vérifier l'existence d'un compte CIVICA et son identité avant de le nommer responsable lors de la création d'une organisation."
    )
    @GetMapping("/users/lookup")
    public ResponseEntity<UtilisateurAdminDto> lookupUserByEmail(@RequestParam("email") String email) {
        return ResponseEntity.ok(adminService.lookupUserByEmail(email));
    }

    @Operation(summary = "Créer un utilisateur administratif ou citoyen")
    @PostMapping("/users")
    public ResponseEntity<UtilisateurAdminDto> createUser(@Valid @RequestBody CreateUserAdminRequest request) {
        return ResponseEntity.ok(adminService.createUser(request));
    }

    @Operation(summary = "Mettre à jour le profil/rôle/service d'un utilisateur")
    @PutMapping("/users/{userId}")
    public ResponseEntity<UtilisateurAdminDto> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserAdminRequest request) {
        return ResponseEntity.ok(adminService.updateUser(userId, request));
    }

    @Operation(summary = "Activer ou désactiver un utilisateur")
    @PutMapping("/users/{userId}/toggle-status")
    public ResponseEntity<UtilisateurAdminDto> toggleUserStatus(@PathVariable Long userId) {
        return ResponseEntity.ok(adminService.toggleUserStatus(userId));
    }

    @Operation(summary = "Lister toutes les organisations (actives et inactives)")
    @GetMapping("/organisations")
    public ResponseEntity<List<Organisation>> getAllOrganisations() {
        return ResponseEntity.ok(adminService.getAllOrganisations());
    }

    @Operation(summary = "Créer une nouvelle organisation administrative")
    @PostMapping("/organisations")
    public ResponseEntity<Organisation> createOrganisation(@Valid @RequestBody CreateOrganisationRequest request) {
        return ResponseEntity.ok(adminService.createOrganisation(request));
    }

    @Operation(summary = "Modifier une organisation administrative")
    @PutMapping("/organisations/{id}")
    public ResponseEntity<Organisation> updateOrganisation(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrganisationRequest request) {
        return ResponseEntity.ok(adminService.updateOrganisation(id, request));
    }

    @Operation(summary = "Supprimer une organisation administrative")
    @DeleteMapping("/organisations/{id}")
    public ResponseEntity<Void> deleteOrganisation(@PathVariable Long id) {
        adminService.deleteOrganisation(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Activer ou désactiver une organisation")
    @PutMapping("/organisations/{id}/toggle-status")
    public ResponseEntity<Organisation> toggleOrganisationStatus(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.toggleOrganisationStatus(id));
    }

    @Operation(summary = "Lister tous les types d'organisations")
    @GetMapping("/organisation-types")
    public ResponseEntity<List<OrganisationType>> getAllOrganisationTypes() {
        return ResponseEntity.ok(adminService.getAllOrganisationTypes());
    }

    @Operation(summary = "Créer un nouveau type d'organisation")
    @PostMapping("/organisation-types")
    public ResponseEntity<OrganisationType> createOrganisationType(
            @Valid @RequestBody CreateOrganisationTypeRequest request) {
        return ResponseEntity.ok(adminService.createOrganisationType(request));
    }

    @Operation(summary = "Modifier un type d'organisation")
    @PutMapping("/organisation-types/{id}")
    public ResponseEntity<OrganisationType> updateOrganisationType(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrganisationTypeRequest request) {
        return ResponseEntity.ok(adminService.updateOrganisationType(id, request));
    }

    @Operation(summary = "Activer ou désactiver un type d'organisation")
    @PutMapping("/organisation-types/{id}/toggle-status")
    public ResponseEntity<OrganisationType> toggleOrganisationTypeStatus(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.toggleOrganisationTypeStatus(id));
    }

    @Operation(summary = "Supprimer un type d'organisation")
    @DeleteMapping("/organisation-types/{id}")
    public ResponseEntity<Void> deleteOrganisationType(@PathVariable Long id) {
        adminService.deleteOrganisationType(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lister tous les services administratifs")
    @GetMapping("/services")
    public ResponseEntity<List<ServiceAdminDto>> getAllServices() {
        return ResponseEntity.ok(adminService.getAllServices());
    }

    @Operation(summary = "Créer un nouveau service administratif au sein d'une organisation")
    @PostMapping("/services")
    public ResponseEntity<ServiceAdminDto> createService(@Valid @RequestBody CreateServiceRequest request) {
        return ResponseEntity.ok(adminService.createService(request));
    }

    @Operation(summary = "Modifier un service administratif")
    @PutMapping("/services/{id}")
    public ResponseEntity<ServiceAdminDto> updateService(
            @PathVariable Long id,
            @Valid @RequestBody UpdateServiceRequest request) {
        return ResponseEntity.ok(adminService.updateService(id, request));
    }

    @Operation(summary = "Supprimer un service administratif")
    @DeleteMapping("/services/{id}")
    public ResponseEntity<Void> deleteService(@PathVariable Long id) {
        adminService.deleteService(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Activer ou désactiver un service administratif")
    @PutMapping("/services/{id}/toggle-status")
    public ResponseEntity<ServiceAdminDto> toggleServiceStatus(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.toggleServiceStatus(id));
    }

    @Operation(summary = "Lister toutes les démarches (actives et inactives)")
    @GetMapping("/demarches")
    public ResponseEntity<List<Demarche>> getAllDemarches() {
        return ResponseEntity.ok(adminService.getAllDemarches());
    }

    @Operation(summary = "Créer une nouvelle démarche au catalogue")
    @PostMapping("/demarches")
    public ResponseEntity<Demarche> createDemarche(@Valid @RequestBody CreateDemarcheRequest request) {
        return ResponseEntity.ok(adminService.createDemarche(request));
    }

    @Operation(summary = "Modifier une démarche du catalogue")
    @PutMapping("/demarches/{id}")
    public ResponseEntity<Demarche> updateDemarche(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDemarcheRequest request) {
        return ResponseEntity.ok(adminService.updateDemarche(id, request));
    }

    @Operation(summary = "Supprimer une démarche du catalogue")
    @DeleteMapping("/demarches/{id}")
    public ResponseEntity<Void> deleteDemarche(@PathVariable Long id) {
        adminService.deleteDemarche(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Activer ou désactiver une démarche")
    @PutMapping("/demarches/{id}/toggle-status")
    public ResponseEntity<Demarche> toggleDemarcheStatus(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.toggleDemarcheStatus(id));
    }
}
