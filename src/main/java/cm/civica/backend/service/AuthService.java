package cm.civica.backend.service;

import cm.civica.backend.dto.JwtResponse;
import cm.civica.backend.dto.LoginRequest;
import cm.civica.backend.dto.ResendCodeRequest;
import cm.civica.backend.dto.SignupRequest;
import cm.civica.backend.dto.VerifyEmailRequest;
import cm.civica.backend.enums.Role;
import cm.civica.backend.exception.BusinessException;
import cm.civica.backend.model.Utilisateur;
import cm.civica.backend.repository.UtilisateurRepository;
import cm.civica.backend.security.JwtUtils;
import cm.civica.backend.security.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private EmailService emailService;

    private final SecureRandom secureRandom = new SecureRandom();

    // ─────────────────────────────────────────────────────────────────────────
    // INSCRIPTION — envoie un OTP, ne retourne PAS de JWT
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public Map<String, String> signup(SignupRequest signupRequest) {
        if (utilisateurRepository.existsByEmail(signupRequest.getEmail())) {
            throw BusinessException.conflict("Un compte avec cette adresse email existe déjà.");
        }
        if (signupRequest.getTelephone() != null
                && !signupRequest.getTelephone().isBlank()
                && utilisateurRepository.existsByTelephone(signupRequest.getTelephone())) {
            throw BusinessException.conflict("Un compte avec ce numéro de téléphone existe déjà.");
        }

        String otp = genererOtp();

        Utilisateur utilisateur = Utilisateur.builder()
                .email(signupRequest.getEmail())
                .motDePasseHash(passwordEncoder.encode(signupRequest.getPassword()))
                .nom(signupRequest.getNom())
                .prenom(signupRequest.getPrenom())
                .telephone(signupRequest.getTelephone())
                .role(Role.ROLE_CITOYEN)
                .actif(true)
                .emailVerifie(false)
                .codeVerification(otp)
                .codeVerificationExpiration(Instant.now().plusSeconds(900)) // 15 min
                .build();

        utilisateurRepository.save(utilisateur);
        emailService.sendVerificationEmail(utilisateur, otp);

        return Map.of("message",
                "Inscription réussie. Un code de vérification a été envoyé à l'adresse " + utilisateur.getEmail() + ".");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VÉRIFICATION EMAIL — valide l'OTP et retourne le JWT
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public JwtResponse verifyEmail(VerifyEmailRequest request) {
        Utilisateur utilisateur = utilisateurRepository.findByIdentifier(request.getEmail())
                .orElseThrow(() -> BusinessException.notFound("Aucun compte trouvé avec cet email."));

        if (Boolean.TRUE.equals(utilisateur.isEmailVerifie())) {
            throw BusinessException.conflict("Ce compte est déjà vérifié. Vous pouvez vous connecter.");
        }
        if (utilisateur.getCodeVerification() == null
                || !utilisateur.getCodeVerification().equals(request.getCode())) {
            throw BusinessException.badRequest("Code de vérification incorrect.");
        }
        if (utilisateur.getCodeVerificationExpiration() == null
                || utilisateur.getCodeVerificationExpiration().isBefore(Instant.now())) {
            throw BusinessException.badRequest("Le code de vérification a expiré. Veuillez en demander un nouveau.");
        }

        utilisateur.setEmailVerifie(true);
        utilisateur.setCodeVerification(null);
        utilisateur.setCodeVerificationExpiration(null);
        utilisateurRepository.save(utilisateur);

        String jwt = jwtUtils.generateTokenFromUsername(utilisateur.getEmail());

        return JwtResponse.builder()
                .token(jwt)
                .type("Bearer")
                .id(utilisateur.getId())
                .email(utilisateur.getEmail())
                .nom(utilisateur.getNom())
                .prenom(utilisateur.getPrenom())
                .telephone(utilisateur.getTelephone())
                .role(utilisateur.getRole().name())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RENVOI DU CODE — génère un nouvel OTP et le renvoie par email
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public Map<String, String> resendCode(ResendCodeRequest request) {
        Utilisateur utilisateur = utilisateurRepository.findByIdentifier(request.getEmail())
                .orElseThrow(() -> BusinessException.notFound("Aucun compte trouvé avec cet email."));

        if (Boolean.TRUE.equals(utilisateur.isEmailVerifie())) {
            throw BusinessException.conflict("Ce compte est déjà vérifié. Vous pouvez vous connecter.");
        }

        String otp = genererOtp();
        utilisateur.setCodeVerification(otp);
        utilisateur.setCodeVerificationExpiration(Instant.now().plusSeconds(900));
        utilisateurRepository.save(utilisateur);
        emailService.sendVerificationEmail(utilisateur, otp);

        return Map.of("message",
                "Un nouveau code de vérification a été envoyé à " + utilisateur.getEmail() + ".");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONNEXION — login par email OU téléphone
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public JwtResponse signin(LoginRequest loginRequest, HttpServletRequest httpRequest) {
        // Résolution de l'identifiant → trouver l'utilisateur réel
        String identifiant = loginRequest.getIdentifiant() != null
                ? loginRequest.getIdentifiant()
                : loginRequest.getEmail();

        Utilisateur utilisateur = utilisateurRepository.findByIdentifier(identifiant)
                .orElseThrow(() -> BusinessException.unauthorized("Identifiant ou mot de passe incorrect."));

        // Vérifier l'email avant d'authentifier
        if (!Boolean.TRUE.equals(utilisateur.isEmailVerifie())) {
            throw new BusinessException(
                    "Votre adresse email n'est pas encore vérifiée. "
                    + "Veuillez consulter votre boîte mail et entrer le code reçu.",
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    Map.of("email", utilisateur.getEmail()));
        }

        // Spring Security authentifie toujours via email (clé unique)
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(utilisateur.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // Alerte email de connexion (asynchrone)
        String ip = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        emailService.sendLoginNotificationEmail(utilisateur, ip, userAgent);

        return JwtResponse.builder()
                .token(jwt)
                .type("Bearer")
                .id(userDetails.getId())
                .email(userDetails.getEmail())
                .nom(userDetails.getNom())
                .prenom(userDetails.getPrenom())
                .telephone(userDetails.getTelephone())
                .role(userDetails.getRole())
                .serviceId(userDetails.getServiceId())
                .serviceNom(userDetails.getServiceNom())
                .organisationId(userDetails.getOrganisationId())
                .organisationNom(userDetails.getOrganisationNom())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UTILITAIRES
    // ─────────────────────────────────────────────────────────────────────────
    public Utilisateur getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw BusinessException.unauthorized("Utilisateur non authentifié.");
        }
        String email = auth.getName();
        return utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> BusinessException.notFound("Utilisateur courant introuvable."));
    }

    public Optional<Utilisateur> getCurrentUserOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        return utilisateurRepository.findByEmail(auth.getName());
    }

    private String genererOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}

