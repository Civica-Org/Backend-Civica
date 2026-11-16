package cm.civica.backend.controller;

import cm.civica.backend.dto.ChangePasswordRequest;
import cm.civica.backend.dto.UpdateProfileRequest;
import cm.civica.backend.dto.UtilisateurAdminDto;
import cm.civica.backend.service.ProfilService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profil")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Profil Utilisateur", description = "Gestion des informations personnelles et du mot de passe de l'utilisateur connecté")
public class ProfilController {

    @Autowired
    private ProfilService profilService;

    @Operation(summary = "Récupérer mon profil", description = "Retourne les informations personnelles du compte connecté.")
    @GetMapping
    public ResponseEntity<UtilisateurAdminDto> getProfil() {
        return ResponseEntity.ok(profilService.getProfil());
    }

    @Operation(summary = "Mettre à jour mes informations", description = "Permet de modifier le nom, le prénom et le numéro de téléphone du compte connecté.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil mis à jour"),
            @ApiResponse(responseCode = "400", description = "Données invalides"),
            @ApiResponse(responseCode = "409", description = "Numéro de téléphone déjà utilisé")
    })
    @PutMapping
    public ResponseEntity<UtilisateurAdminDto> updateProfil(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(profilService.updateProfil(request));
    }

    @Operation(summary = "Changer mon mot de passe", description = "Vérifie le mot de passe actuel puis enregistre le nouveau mot de passe chiffré.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mot de passe modifié"),
            @ApiResponse(responseCode = "400", description = "Mot de passe actuel incorrect ou nouveau mot de passe invalide")
    })
    @PutMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(profilService.changePassword(request));
    }
}
