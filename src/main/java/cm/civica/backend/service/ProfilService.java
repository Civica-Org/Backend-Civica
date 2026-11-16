package cm.civica.backend.service;

import cm.civica.backend.dto.ChangePasswordRequest;
import cm.civica.backend.dto.UpdateProfileRequest;
import cm.civica.backend.dto.UtilisateurAdminDto;
import cm.civica.backend.exception.BusinessException;
import cm.civica.backend.model.Utilisateur;
import cm.civica.backend.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Service de gestion du profil personnel (informations et mot de passe)
 * accessible à tout utilisateur authentifié quel que soit son rôle.
 */
@Service
public class ProfilService {

    @Autowired
    private AuthService authService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UtilisateurAdminDto getProfil() {
        return UtilisateurAdminDto.fromEntity(authService.getCurrentUser());
    }

    @Transactional
    public UtilisateurAdminDto updateProfil(UpdateProfileRequest request) {
        Utilisateur user = authService.getCurrentUser();

        String nom = request.getNom().trim();
        String prenom = request.getPrenom().trim();
        String rawTel = request.getTelephone();
        String telephone = (rawTel != null && !rawTel.isBlank()) ? rawTel.trim() : null;

        if (telephone != null
                && !telephone.equals(user.getTelephone())
                && utilisateurRepository.existsByTelephone(telephone)) {
            throw BusinessException.conflict("Ce numéro de téléphone est déjà associé à un autre compte.");
        }

        user.setNom(nom);
        user.setPrenom(prenom);
        user.setTelephone(telephone);

        return UtilisateurAdminDto.fromEntity(utilisateurRepository.save(user));
    }

    @Transactional
    public Map<String, String> changePassword(ChangePasswordRequest request) {
        Utilisateur user = authService.getCurrentUser();

        if (!passwordEncoder.matches(request.getAncienMotDePasse(), user.getMotDePasseHash())) {
            throw BusinessException.badRequest("Le mot de passe actuel est incorrect.");
        }

        if (request.getConfirmationMotDePasse() != null
                && !request.getConfirmationMotDePasse().isBlank()
                && !request.getNouveauMotDePasse().equals(request.getConfirmationMotDePasse())) {
            throw BusinessException.badRequest("La confirmation du nouveau mot de passe ne correspond pas.");
        }

        if (request.getNouveauMotDePasse().equals(request.getAncienMotDePasse())) {
            throw BusinessException.badRequest("Le nouveau mot de passe doit être différent de l'ancien.");
        }

        user.setMotDePasseHash(passwordEncoder.encode(request.getNouveauMotDePasse()));
        utilisateurRepository.save(user);

        return Map.of("message", "Votre mot de passe a été modifié avec succès.");
    }
}
