package cm.civica.backend.service;

import cm.civica.backend.dto.CreateUserAdminRequest;
import cm.civica.backend.dto.ServiceAdminDto;
import cm.civica.backend.dto.UtilisateurAdminDto;
import cm.civica.backend.enums.Role;
import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.exception.BusinessException;
import cm.civica.backend.model.Dossier;
import cm.civica.backend.model.Organisation;
import cm.civica.backend.model.ServiceAdministratif;
import cm.civica.backend.model.Utilisateur;
import cm.civica.backend.repository.DossierRepository;
import cm.civica.backend.repository.OrganisationRepository;
import cm.civica.backend.repository.ServiceAdministratifRepository;
import cm.civica.backend.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Helper partagé garantissant les invariants hiérarchiques :
 * - Une Organisation contient plusieurs services et au plus un Responsable (ROLE_RESPONSABLE).
 * - Un Service administratif contient plusieurs agents (ROLE_AGENT) et au plus un Sous-Responsable (ROLE_SOUS_RESPONSABLE).
 */
@Component
public class PersonnelHierarchyHelper {

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private ServiceAdministratifRepository serviceRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Organisation requireOrganisation(Long organisationId, String errorMessage) {
        if (organisationId == null || organisationId <= 0) {
            throw BusinessException.badRequest(errorMessage);
        }
        return organisationRepository.findById(organisationId)
                .orElseThrow(() -> BusinessException.notFound("Organisation non trouvée avec l'ID : " + organisationId));
    }

    public ServiceAdministratif requireService(Long serviceId, String errorMessage) {
        if (serviceId == null || serviceId <= 0) {
            throw BusinessException.badRequest(errorMessage);
        }
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> BusinessException.notFound("Service administratif introuvable avec l'ID : " + serviceId));
    }

    public Organisation requireUserOrganisation(Utilisateur user) {
        Organisation org = user.resolveEffectiveOrganisation();
        if (org == null) {
            throw BusinessException.forbidden("Aucune organisation administrative n'est rattachée à votre compte.");
        }
        return org;
    }

    public ServiceAdministratif requireUserService(Utilisateur user) {
        if (user.getService() == null) {
            throw BusinessException.forbidden("Aucun service administratif n'est rattaché à votre compte.");
        }
        return user.getService();
    }

    /**
     * Vérifie les règles métier de rattachement et d'unicité selon le rôle, puis applique l'affiliation.
     */
    public void assignAffiliation(Utilisateur targetUser, Role role, Long organisationId, Long serviceId) {
        Long excludeUserId = targetUser.getId();

        switch (role) {
            case ROLE_CITOYEN, ROLE_SUPER_ADMIN -> {
                targetUser.setOrganisation(null);
                targetUser.setService(null);
            }
            case ROLE_AGENT -> {
                ServiceAdministratif svc = requireService(
                        serviceId,
                        "Un agent instructeur doit obligatoirement être rattaché à un service administratif."
                );
                targetUser.setService(svc);
                targetUser.setOrganisation(null);
            }
            case ROLE_SOUS_RESPONSABLE -> {
                ServiceAdministratif svc = requireService(
                        serviceId,
                        "Un sous-responsable doit obligatoirement être rattaché à un service administratif."
                );
                utilisateurRepository.findByServiceIdAndRole(svc.getId(), Role.ROLE_SOUS_RESPONSABLE).stream()
                        .filter(existing -> !Objects.equals(existing.getId(), excludeUserId))
                        .findFirst()
                        .ifPresent(existing -> {
                            throw BusinessException.conflict(
                                    "Le service \"" + svc.getNom() + "\" dispose déjà d'un sous-responsable ("
                                    + existing.getPrenom() + " " + existing.getNom()
                                    + "). Un service ne peut contenir qu'un seul sous-responsable.");
                        });
                targetUser.setService(svc);
                targetUser.setOrganisation(null);
            }
            case ROLE_RESPONSABLE -> {
                Organisation org = requireOrganisation(
                        organisationId,
                        "Un responsable doit obligatoirement être rattaché à une organisation."
                );
                utilisateurRepository.findByOrganisationIdAndRole(org.getId(), Role.ROLE_RESPONSABLE).stream()
                        .filter(existing -> !Objects.equals(existing.getId(), excludeUserId))
                        .findFirst()
                        .ifPresent(existing -> {
                            throw BusinessException.conflict(
                                    "L'organisation \"" + org.getNom() + "\" dispose déjà d'un responsable ("
                                    + existing.getPrenom() + " " + existing.getNom()
                                    + "). Une organisation ne peut contenir qu'un seul responsable.");
                        });
                utilisateurRepository.findByServiceOrganisationIdAndRole(org.getId(), Role.ROLE_RESPONSABLE).stream()
                        .filter(existing -> !Objects.equals(existing.getId(), excludeUserId))
                        .findFirst()
                        .ifPresent(existing -> {
                            throw BusinessException.conflict(
                                    "L'organisation \"" + org.getNom() + "\" dispose déjà d'un responsable ("
                                    + existing.getPrenom() + " " + existing.getNom()
                                    + "). Une organisation ne peut contenir qu'un seul responsable.");
                        });
                targetUser.setOrganisation(org);
                targetUser.setService(null);
            }
        }
    }

    /**
     * Crée et persiste un utilisateur avec validation d'unicité email/téléphone et affiliation hiérarchique.
     */
    public UtilisateurAdminDto createInternalUser(CreateUserAdminRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (utilisateurRepository.existsByEmail(normalizedEmail)) {
            throw BusinessException.badRequest("Un utilisateur existe déjà avec cet email : " + normalizedEmail);
        }

        String tel = (request.getTelephone() != null && !request.getTelephone().isBlank())
                ? request.getTelephone().trim()
                : null;
        if (tel != null && utilisateurRepository.existsByTelephone(tel)) {
            throw BusinessException.conflict("Ce numéro de téléphone est déjà associé à un autre compte : " + tel);
        }

        String rawPassword = (request.getMotDePasse() != null && !request.getMotDePasse().isBlank())
                ? request.getMotDePasse()
                : "Civica@2026!";

        Utilisateur user = Utilisateur.builder()
                .email(normalizedEmail)
                .nom(request.getNom().trim())
                .prenom(request.getPrenom().trim())
                .telephone(tel)
                .role(request.getRole())
                .motDePasseHash(passwordEncoder.encode(rawPassword))
                .actif(true)
                .emailVerifie(true)
                .build();

        assignAffiliation(user, request.getRole(), request.getOrganisationId(), request.getServiceId());
        return UtilisateurAdminDto.fromEntity(utilisateurRepository.save(user));
    }

    /**
     * Applique une mise à jour de rôle, d'affiliation et de statut actif sur un utilisateur existant.
     */
    public UtilisateurAdminDto applyUserUpdate(
            Utilisateur user,
            Role targetRole,
            Long targetOrganisationId,
            Long targetServiceId,
            Boolean actif) {
        user.setRole(targetRole);
        assignAffiliation(user, targetRole, targetOrganisationId, targetServiceId);
        if (actif != null) {
            user.setActif(actif);
        }
        return UtilisateurAdminDto.fromEntity(utilisateurRepository.save(user));
    }

    /**
     * Vérifie le droit d'accès d'un utilisateur interne à un dossier donné.
     */
    public void verifyDossierAccess(Utilisateur user, Dossier dossier) {
        if (user.getRole() == Role.ROLE_RESPONSABLE) {
            Organisation ownOrg = requireUserOrganisation(user);
            if (dossier.getServiceCourant() == null
                    || dossier.getServiceCourant().getOrganisation() == null
                    || !ownOrg.getId().equals(dossier.getServiceCourant().getOrganisation().getId())) {
                throw BusinessException.forbidden("Accès refusé : ce dossier n'appartient pas à votre organisation.");
            }
            return;
        }

        ServiceAdministratif ownService = requireUserService(user);
        if (dossier.getServiceCourant() == null || !ownService.getId().equals(dossier.getServiceCourant().getId())) {
            throw BusinessException.forbidden("Accès refusé : ce dossier n'est pas affecté à votre service.");
        }
    }

    /**
     * Retourne les dossiers visibles pour l'utilisateur courant selon son périmètre
     * (toute l'organisation pour ROLE_RESPONSABLE, son service uniquement pour ROLE_AGENT / ROLE_SOUS_RESPONSABLE).
     */
    public List<Dossier> findScopedDossiers(Utilisateur user, StatutDossier statut, Long requestedServiceId) {
        if (user.getRole() == Role.ROLE_RESPONSABLE) {
            Organisation ownOrg = requireUserOrganisation(user);
            if (requestedServiceId != null) {
                ServiceAdministratif svc = requireService(
                        requestedServiceId,
                        "Service administratif introuvable avec l'ID : " + requestedServiceId
                );
                if (svc.getOrganisation() == null || !ownOrg.getId().equals(svc.getOrganisation().getId())) {
                    throw BusinessException.forbidden("Accès refusé : ce service n'appartient pas à votre organisation.");
                }
                return statut != null
                        ? dossierRepository.findByServiceCourantIdAndStatut(svc.getId(), statut)
                        : dossierRepository.findByServiceCourantId(svc.getId());
            }
            return statut != null
                    ? dossierRepository.findByServiceCourantOrganisationIdAndStatut(ownOrg.getId(), statut)
                    : dossierRepository.findByServiceCourantOrganisationId(ownOrg.getId());
        }

        ServiceAdministratif ownService = requireUserService(user);
        if (requestedServiceId != null && !requestedServiceId.equals(ownService.getId())) {
            throw BusinessException.forbidden("Accès refusé : vous ne pouvez consulter que les dossiers de votre service.");
        }
        return statut != null
                ? dossierRepository.findByServiceCourantIdAndStatut(ownService.getId(), statut)
                : dossierRepository.findByServiceCourantId(ownService.getId());
    }

    public ServiceAdminDto toServiceAdminDto(ServiceAdministratif s) {
        return ServiceAdminDto.builder()
                .id(s.getId())
                .code(s.getCode())
                .nom(s.getNom())
                .actif(s.getActif())
                .organisationId(s.getOrganisation() != null ? s.getOrganisation().getId() : null)
                .organisationCode(s.getOrganisation() != null ? s.getOrganisation().getCode() : null)
                .organisationNom(s.getOrganisation() != null ? s.getOrganisation().getNom() : null)
                .build();
    }
}
