package cm.civica.backend.service;

import cm.civica.backend.dto.*;
import cm.civica.backend.enums.Role;
import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.exception.BusinessException;
import cm.civica.backend.model.Dossier;
import cm.civica.backend.model.Organisation;
import cm.civica.backend.model.ServiceAdministratif;
import cm.civica.backend.model.Utilisateur;
import cm.civica.backend.pattern.chain.CircuitValidationDossier;
import cm.civica.backend.pattern.chain.ResultatValidation;
import cm.civica.backend.pattern.observer.DossierEvent;
import cm.civica.backend.pattern.observer.DossierSubject;
import cm.civica.backend.pattern.state.DossierStateMachine;
import cm.civica.backend.repository.DossierRepository;
import cm.civica.backend.repository.HistoriqueDossierRepository;
import cm.civica.backend.repository.ServiceAdministratifRepository;
import cm.civica.backend.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service dédié aux rôles de supervision :
 * - ROLE_RESPONSABLE (Organisation -> tous les services de son organisation + équipe)
 * - ROLE_SOUS_RESPONSABLE (Service -> son service et ses agents)
 */
@Service
public class ResponsableService {

    private static final Logger log = LoggerFactory.getLogger(ResponsableService.class);

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private ServiceAdministratifRepository serviceRepository;

    @Autowired
    private HistoriqueDossierRepository historiqueRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private CitoyenService citoyenService;

    @Autowired
    private DossierStateMachine stateMachine;

    @Autowired
    private CircuitValidationDossier circuitValidation;

    @Autowired
    private DossierSubject dossierSubject;

    @Autowired
    private PersonnelHierarchyHelper personnelHierarchyHelper;

    @Transactional(readOnly = true)
    public DashboardResponsableDto getDashboard() {
        return getDashboard(null);
    }

    /**
     * Calcule l'ensemble des indicateurs de supervision pour le tableau de bord du Responsable ou Sous-Responsable.
     */
    @Transactional(readOnly = true)
    public DashboardResponsableDto getDashboard(Long requestedServiceId) {
        Utilisateur responsable = authService.getCurrentUser();
        List<Dossier> dossiers = personnelHierarchyHelper.findScopedDossiers(responsable, null, requestedServiceId);

        String codeScope;
        String nomScope;
        String nomOrganisation;

        if (requestedServiceId != null) {
            ServiceAdministratif svc = personnelHierarchyHelper.requireService(
                    requestedServiceId, "Service administratif introuvable avec l'ID : " + requestedServiceId);
            codeScope = svc.getCode();
            nomScope = svc.getNom();
            nomOrganisation = svc.getOrganisation() != null ? svc.getOrganisation().getNom() : "République du Cameroun";
        } else if (responsable.getRole() == Role.ROLE_RESPONSABLE) {
            Organisation org = personnelHierarchyHelper.requireUserOrganisation(responsable);
            int nbServices = serviceRepository.findByOrganisationId(org.getId()).size();
            codeScope = org.getCode();
            nomScope = nbServices == 0 ? "Aucun service configuré" : "Tous les services (" + nbServices + ")";
            nomOrganisation = org.getNom();
        } else {
            ServiceAdministratif svc = personnelHierarchyHelper.requireUserService(responsable);
            codeScope = svc.getCode();
            nomScope = svc.getNom();
            nomOrganisation = svc.getOrganisation() != null ? svc.getOrganisation().getNom() : "République du Cameroun";
        }

        long total = dossiers.size();
        long enAttente = dossiers.stream().filter(d -> d.getStatut() == StatutDossier.SOUMIS).count();
        long enCours = dossiers.stream().filter(d -> d.getStatut() == StatutDossier.EN_VERIFICATION).count();
        long valides = dossiers.stream().filter(d -> d.getStatut() == StatutDossier.VALIDE).count();
        long rejetes = dossiers.stream().filter(d -> d.getStatut() == StatutDossier.REJETE).count();
        long transferes = dossiers.stream().filter(d -> d.getStatut() == StatutDossier.TRANSFERE).count();

        long traites = valides + rejetes;
        double tauxVal = traites > 0 ? Math.round(((double) valides / traites) * 1000.0) / 10.0 : 0.0;
        double tauxRej = traites > 0 ? Math.round(((double) rejetes / traites) * 1000.0) / 10.0 : 0.0;

        // Calcul du délai moyen de traitement en jours
        double delaiMoyen = dossiers.stream()
                .filter(d -> (d.getStatut() == StatutDossier.VALIDE || d.getStatut() == StatutDossier.REJETE) && d.getCreatedAt() != null && d.getUpdatedAt() != null)
                .mapToLong(d -> Duration.between(d.getCreatedAt(), d.getUpdatedAt()).toHours())
                .average()
                .orElse(0.0) / 24.0;
        delaiMoyen = Math.round(delaiMoyen * 10.0) / 10.0;

        // Détection des dossiers en souffrance (dépassant le délai indicatif)
        List<Dossier> dossiersEnSouffranceList = dossiers.stream()
                .filter(d -> d.getStatut() == StatutDossier.SOUMIS || d.getStatut() == StatutDossier.EN_VERIFICATION)
                .filter(d -> {
                    if (d.getDemarche() == null || d.getDemarche().getDelaiIndicatifJours() == null || d.getCreatedAt() == null) {
                        return false;
                    }
                    long joursEcoules = Duration.between(d.getCreatedAt(), Instant.now()).toDays();
                    return joursEcoules > d.getDemarche().getDelaiIndicatifJours();
                })
                .collect(Collectors.toList());

        // Répartition par démarche
        Map<String, Long> repartition = dossiers.stream()
                .filter(d -> d.getDemarche() != null)
                .collect(Collectors.groupingBy(d -> d.getDemarche().getTitre(), Collectors.counting()));

        List<AgentChargeDto> agentsCharge = getAgentsCharge(requestedServiceId);

        return DashboardResponsableDto.builder()
                .codeService(codeScope)
                .nomService(nomScope)
                .nomOrganisation(nomOrganisation)
                .totalDossiers(total)
                .dossiersEnAttente(enAttente)
                .dossiersEnCours(enCours)
                .dossiersValides(valides)
                .dossiersRejetes(rejetes)
                .dossiersTransferes(transferes)
                .tauxValidation(tauxVal)
                .tauxRejet(tauxRej)
                .delaiMoyenJours(delaiMoyen)
                .nombreDossiersEnRetard(dossiersEnSouffranceList.size())
                .repartitionParDemarche(repartition)
                .agentsCharge(agentsCharge)
                .dossiersEnSouffrance(dossiersEnSouffranceList.stream().map(citoyenService::toDto).collect(Collectors.toList()))
                .build();
    }

    /**
     * Liste des agents et sous-responsables avec leur charge de travail.
     */
    @Transactional(readOnly = true)
    public List<AgentChargeDto> getAgentsCharge(Long requestedServiceId) {
        Utilisateur responsable = authService.getCurrentUser();
        List<Utilisateur> membres;

        if (responsable.getRole() == Role.ROLE_RESPONSABLE) {
            Organisation ownOrg = personnelHierarchyHelper.requireUserOrganisation(responsable);
            if (requestedServiceId != null) {
                ServiceAdministratif svc = personnelHierarchyHelper.requireService(
                        requestedServiceId, "Service administratif introuvable avec l'ID : " + requestedServiceId);
                if (svc.getOrganisation() == null || !ownOrg.getId().equals(svc.getOrganisation().getId())) {
                    throw BusinessException.forbidden("Accès refusé : ce service n'appartient pas à votre organisation.");
                }
                membres = utilisateurRepository.findByServiceId(svc.getId());
            } else {
                membres = utilisateurRepository.findByServiceOrganisationId(ownOrg.getId());
            }
        } else {
            ServiceAdministratif ownService = personnelHierarchyHelper.requireUserService(responsable);
            if (requestedServiceId != null && !requestedServiceId.equals(ownService.getId())) {
                throw BusinessException.forbidden("Accès refusé : vous ne pouvez consulter que les agents de votre propre service.");
            }
            membres = utilisateurRepository.findByServiceId(ownService.getId());
        }

        return membres.stream()
                .filter(u -> u.getRole() == Role.ROLE_AGENT || u.getRole() == Role.ROLE_SOUS_RESPONSABLE)
                .sorted(Comparator
                        .comparing((Utilisateur u) -> u.getService() != null ? u.getService().getNom() : "")
                        .thenComparing(u -> u.getRole() == Role.ROLE_SOUS_RESPONSABLE ? 0 : 1)
                        .thenComparing(Utilisateur::getNom))
                .map(agent -> {
                    long enCours = historiqueRepository.countByAgentIdAndNouveauStatut(agent.getId(), StatutDossier.EN_VERIFICATION);
                    long totalTraite = historiqueRepository.countByAgentId(agent.getId());

                    return AgentChargeDto.builder()
                            .agentId(agent.getId())
                            .nomComplet(agent.getPrenom() + " " + agent.getNom())
                            .email(agent.getEmail())
                            .telephone(agent.getTelephone())
                            .role(agent.getRole().name())
                            .serviceId(agent.getService() != null ? agent.getService().getId() : null)
                            .serviceNom(agent.getService() != null ? agent.getService().getNom() : null)
                            .dossiersEnCours(enCours)
                            .dossiersTraitesTotal(totalTraite)
                            .actif(Boolean.TRUE.equals(agent.getActif()))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Retourne les services du périmètre de supervision.
     */
    @Transactional(readOnly = true)
    public List<ServiceAdminDto> getServicesPerimetre() {
        Utilisateur current = authService.getCurrentUser();
        if (current.getRole() == Role.ROLE_RESPONSABLE) {
            Organisation ownOrg = personnelHierarchyHelper.requireUserOrganisation(current);
            return serviceRepository.findByOrganisationId(ownOrg.getId()).stream()
                    .map(personnelHierarchyHelper::toServiceAdminDto)
                    .collect(Collectors.toList());
        }
        ServiceAdministratif ownService = personnelHierarchyHelper.requireUserService(current);
        return List.of(personnelHierarchyHelper.toServiceAdminDto(ownService));
    }

    /**
     * Réassigne un dossier à un agent ou sous-responsable du service détenteur.
     */
    @Transactional
    public DossierResponseDto reassignerDossier(String numeroSuivi, ReassignationDossierRequest request) {
        Utilisateur responsable = authService.getCurrentUser();
        Dossier dossier = dossierRepository.findByNumeroSuivi(numeroSuivi)
                .orElseThrow(() -> BusinessException.notFound("Dossier introuvable : " + numeroSuivi));
        personnelHierarchyHelper.verifyDossierAccess(responsable, dossier);

        Utilisateur agentCible = utilisateurRepository.findById(request.getAgentId())
                .orElseThrow(() -> BusinessException.notFound("Agent cible introuvable : " + request.getAgentId()));

        if (agentCible.getRole() != Role.ROLE_AGENT && agentCible.getRole() != Role.ROLE_SOUS_RESPONSABLE) {
            throw BusinessException.badRequest("L'utilisateur cible doit être un agent ou un sous-responsable.");
        }
        if (dossier.getServiceCourant() == null
                || agentCible.getService() == null
                || !dossier.getServiceCourant().getId().equals(agentCible.getService().getId())) {
            throw BusinessException.forbidden("Vous ne pouvez réassigner ce dossier qu'à un agent ou sous-responsable du service détenteur.");
        }

        StatutDossier ancienStatut = dossier.getStatut();
        dossier.setStatut(StatutDossier.EN_VERIFICATION);
        dossier.setUpdatedAt(Instant.now());
        Dossier saved = dossierRepository.save(dossier);

        String note = request.getNoteResponsable() != null ? request.getNoteResponsable() : "Réassignation par la supervision";

        // Notification des observateurs (Pattern Observer)
        dossierSubject.notifyObservers(DossierEvent.of(
                saved, ancienStatut, StatutDossier.EN_VERIFICATION,
                "REASSIGNATION_AGENT",
                "Dossier réassigné à " + agentCible.getPrenom() + " " + agentCible.getNom() + " par " + responsable.getPrenom() + " " + responsable.getNom() + ". Note: " + note,
                responsable, dossier.getServiceCourant()
        ));

        return citoyenService.toDto(saved);
    }

    /**
     * Validation hiérarchique de second niveau par le Responsable ou Sous-Responsable.
     */
    @Transactional
    public DossierResponseDto validerHierarchique(String numeroSuivi, DecisionDossierRequest request) {
        Utilisateur responsable = authService.getCurrentUser();
        Dossier dossier = dossierRepository.findByNumeroSuiviWithPiecesAndDemarche(numeroSuivi)
                .orElseThrow(() -> BusinessException.notFound("Dossier introuvable : " + numeroSuivi));
        personnelHierarchyHelper.verifyDossierAccess(responsable, dossier);

        StatutDossier ancienStatut = dossier.getStatut();

        if (request.getStatut() == StatutDossier.VALIDE) {
            ResultatValidation res = circuitValidation.executerCircuit(dossier);
            if (!res.isValide()) {
                throw BusinessException.badRequest("Validation impossible : " + res.getMotifRejet());
            }
            if (dossier.getStatut() == StatutDossier.SOUMIS || dossier.getStatut() == StatutDossier.TRANSFERE) {
                stateMachine.prendreEnCharge(dossier);
            }
            stateMachine.valider(dossier);
        } else if (request.getStatut() == StatutDossier.REJETE) {
            stateMachine.rejeter(dossier, request.getMotifRejet());
        } else {
            throw BusinessException.badRequest("Statut hiérarchique non supporté : " + request.getStatut());
        }

        dossier.setUpdatedAt(Instant.now());
        Dossier saved = dossierRepository.save(dossier);

        String comm = request.getCommentaire() != null ? request.getCommentaire() :
                (request.getStatut() == StatutDossier.VALIDE ? "Validation hiérarchique finale accordée." : request.getMotifRejet());

        // Notification des observateurs (Pattern Observer)
        dossierSubject.notifyObservers(DossierEvent.of(
                saved, ancienStatut, request.getStatut(),
                "DECISION_HIERARCHIQUE_" + request.getStatut().name(),
                comm,
                responsable, dossier.getServiceCourant()
        ));

        return citoyenService.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<UtilisateurAdminDto> getEquipeOrganisation() {
        Utilisateur responsable = requireOrganisationResponsable();
        Organisation org = personnelHierarchyHelper.requireUserOrganisation(responsable);

        return utilisateurRepository.findByServiceOrganisationId(org.getId()).stream()
                .filter(u -> u.getRole() == Role.ROLE_AGENT || u.getRole() == Role.ROLE_SOUS_RESPONSABLE)
                .map(UtilisateurAdminDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public UtilisateurAdminDto createMembreEquipe(CreateUserAdminRequest request) {
        Utilisateur responsable = requireOrganisationResponsable();
        Organisation org = personnelHierarchyHelper.requireUserOrganisation(responsable);
        Role requestedRole = validateServiceStaffRole(request.getRole());

        ServiceAdministratif svc = requireOrgOwnedService(org, request.getServiceId());
        CreateUserAdminRequest scopedRequest = CreateUserAdminRequest.builder()
                .email(request.getEmail())
                .nom(request.getNom())
                .prenom(request.getPrenom())
                .telephone(request.getTelephone())
                .motDePasse(request.getMotDePasse())
                .role(requestedRole)
                .serviceId(svc.getId())
                .organisationId(null)
                .build();

        return personnelHierarchyHelper.createInternalUser(scopedRequest);
    }

    @Transactional
    public UtilisateurAdminDto updateMembreEquipe(Long targetUserId, UpdateUserAdminRequest request) {
        Utilisateur responsable = requireOrganisationResponsable();
        Organisation org = personnelHierarchyHelper.requireUserOrganisation(responsable);
        Utilisateur target = requireManagedOrgStaff(targetUserId, org);

        Role targetRole = validateServiceStaffRole(request.getRole() != null ? request.getRole() : target.getRole());
        Long targetServiceId = request.getServiceId() != null
                ? requireOrgOwnedService(org, request.getServiceId()).getId()
                : (target.getService() != null ? target.getService().getId() : null);

        return personnelHierarchyHelper.applyUserUpdate(
                target, targetRole, null, targetServiceId, request.getActif()
        );
    }

    @Transactional
    public UtilisateurAdminDto toggleMembreEquipeActif(Long targetUserId) {
        Utilisateur responsable = requireOrganisationResponsable();
        Organisation org = personnelHierarchyHelper.requireUserOrganisation(responsable);
        Utilisateur target = requireManagedOrgStaff(targetUserId, org);

        target.setActif(!Boolean.TRUE.equals(target.getActif()));
        return UtilisateurAdminDto.fromEntity(utilisateurRepository.save(target));
    }

    private Utilisateur requireOrganisationResponsable() {
        Utilisateur current = authService.getCurrentUser();
        if (current.getRole() != Role.ROLE_RESPONSABLE) {
            throw BusinessException.forbidden("Seul le Responsable de l'organisation peut gérer l'équipe et les affectations.");
        }
        return current;
    }

    private Role validateServiceStaffRole(Role role) {
        if (role != Role.ROLE_AGENT && role != Role.ROLE_SOUS_RESPONSABLE) {
            throw BusinessException.forbidden("Vous ne pouvez attribuer que les rôles Agent Instructeur ou Sous-Responsable de Service.");
        }
        return role;
    }

    private ServiceAdministratif requireOrgOwnedService(Organisation org, Long serviceId) {
        ServiceAdministratif svc = personnelHierarchyHelper.requireService(
                serviceId, "Le service administratif cible est obligatoire."
        );
        if (svc.getOrganisation() == null || !org.getId().equals(svc.getOrganisation().getId())) {
            throw BusinessException.forbidden("Accès refusé : ce service n'appartient pas à votre organisation.");
        }
        return svc;
    }

    private Utilisateur requireManagedOrgStaff(Long userId, Organisation org) {
        Utilisateur target = utilisateurRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("Membre d'équipe introuvable : " + userId));
        if ((target.getRole() != Role.ROLE_AGENT && target.getRole() != Role.ROLE_SOUS_RESPONSABLE)
                || target.getService() == null
                || target.getService().getOrganisation() == null
                || !org.getId().equals(target.getService().getOrganisation().getId())) {
            throw BusinessException.forbidden("Accès refusé : cet utilisateur n'appartient pas aux services de votre organisation.");
        }
        return target;
    }
}
