package cm.civica.backend.controller;

import cm.civica.backend.dto.JwtResponse;
import cm.civica.backend.dto.LoginRequest;
import cm.civica.backend.dto.ResendCodeRequest;
import cm.civica.backend.dto.SignupRequest;
import cm.civica.backend.dto.UtilisateurAdminDto;
import cm.civica.backend.dto.VerifyEmailRequest;
import cm.civica.backend.service.AuthService;
import cm.civica.backend.service.ProfilService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "1. Authentification", description = "Endpoints d'inscription, de vérification email, de connexion et de récupération du profil utilisateur")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private ProfilService profilService;

    // ─── CONNEXION ───────────────────────────────────────────────────────────
    @Operation(
        summary = "Connexion utilisateur",
        description = "Authentifie un utilisateur par email OU numéro de téléphone. L'email doit être vérifié au préalable."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Connexion réussie — JWT retourné"),
            @ApiResponse(responseCode = "401", description = "Identifiants invalides"),
            @ApiResponse(responseCode = "403", description = "Email non vérifié")
    })
    @SecurityRequirements
    @PostMapping("/signin")
    public ResponseEntity<JwtResponse> authenticateUser(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.signin(loginRequest, httpRequest));
    }

    // ─── INSCRIPTION ─────────────────────────────────────────────────────────
    @Operation(
        summary = "Inscription citoyen",
        description = "Crée un compte citoyen et envoie un code OTP de vérification par email. Ne retourne PAS de JWT — il faut d'abord vérifier l'email via /verify-email."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inscription réussie — code de vérification envoyé par email"),
            @ApiResponse(responseCode = "409", description = "Email ou téléphone déjà utilisé")
    })
    @SecurityRequirements
    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> registerUser(@Valid @RequestBody SignupRequest signupRequest) {
        return ResponseEntity.ok(authService.signup(signupRequest));
    }

    // ─── VÉRIFICATION EMAIL ───────────────────────────────────────────────────
    @Operation(
        summary = "Vérification du code OTP",
        description = "Vérifie le code à 6 chiffres reçu par email lors de l'inscription. Retourne un JWT si le code est correct."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email vérifié — JWT retourné"),
            @ApiResponse(responseCode = "400", description = "Code incorrect ou expiré"),
            @ApiResponse(responseCode = "409", description = "Compte déjà vérifié")
    })
    @SecurityRequirements
    @PostMapping("/verify-email")
    public ResponseEntity<JwtResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request));
    }

    // ─── RENVOI DU CODE ───────────────────────────────────────────────────────
    @Operation(
        summary = "Renvoyer le code de vérification",
        description = "Génère un nouveau code OTP et l'envoie par email. Utile si le code précédent a expiré."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nouveau code envoyé"),
            @ApiResponse(responseCode = "404", description = "Compte introuvable"),
            @ApiResponse(responseCode = "409", description = "Compte déjà vérifié")
    })
    @SecurityRequirements
    @PostMapping("/resend-code")
    public ResponseEntity<Map<String, String>> resendVerificationCode(@Valid @RequestBody ResendCodeRequest request) {
        return ResponseEntity.ok(authService.resendCode(request));
    }

    // ─── PROFIL COURANT ───────────────────────────────────────────────────────
    @Operation(summary = "Profil utilisateur courant", description = "Retourne les informations du compte actuellement authentifié.")
    @GetMapping("/me")
    public ResponseEntity<UtilisateurAdminDto> getCurrentUserProfile() {
        return ResponseEntity.ok(profilService.getProfil());
    }
}

