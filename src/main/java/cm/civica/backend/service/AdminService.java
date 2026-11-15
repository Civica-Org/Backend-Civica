package cm.civica.backend.service;

import cm.civica.backend.dto.*;
import cm.civica.backend.enums.Role;
import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.exception.BusinessException;
import cm.civica.backend.model.*;
import cm.civica.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private ServiceAdministratifRepository serviceRepository;

    @Autowired
    private DemarcheRepository demarcheRepository;

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private HistoriqueDossierRepository historiqueRepository;

    @Autowired
    private CitoyenService citoyenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public AdminDashboardStatsDto getDashboardStats() {
        long totalOrgs = organisationRepository.count();
        long totalServices = serviceRepository.count();
        long totalDemarches = demarcheRepository.count();
        long totalUsers = utilisateurRepository.count();
        long totalCitoyens = utilisateurRepository.findByRole(Role.ROLE_CITOYEN).size();
        long totalAgents = utilisateurRepository.findByRole(Role.ROLE_AGENT).size();
        long totalResponsables = utilisateurRepository.findByRole(Role.ROLE_RESPONSABLE).size();
        long totalDossiers = dossierRepository.count();

        List<Dossier> dossiers = dossierRepository.findAll();
        Map<String, Long> dossiersParStatut = new LinkedHashMap<>();
        for (StatutDossier s : StatutDossier.values()) {
            long count = dossiers.stream().filter(d -> d.getStatut() == s).count();
            dossiersParStatut.put(s.name(), count);
        }

        Map<String, Long> dossiersParOrganisation = dossiers.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getServiceCourant() != null && d.getServiceCourant().getOrganisation() != null
                                ? d.getServiceCourant().getOrganisation().getNom()
                                : "Non affecté",
                        Collectors.counting()
                ));

        return AdminDashboardStatsDto.builder()
                .totalOrganisations(totalOrgs)
                .totalServices(totalServices)
                .totalDemarches(totalDemarches)
                .totalUtilisateurs(totalUsers)
                .totalCitoyens(totalCitoyens)
                .totalAgents(totalAgents)
                .totalResponsables(totalResponsables)
                .totalDossiers(totalDossiers)
                .dossiersParStatut(dossiersParStatut)
                .dossiersParOrganisation(dossiersParOrganisation)
                .build();
    }

    @Transactional(readOnly = true)
    public List<UtilisateurAdminDto> getAllUsers() {
        return utilisateurRepository.findAll().stream()
                .map(this::toAdminDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public UtilisateurAdminDto createUser(CreateUserAdminRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (utilisateurRepository.existsByEmail(normalizedEmail)) {
            throw BusinessException.badRequest("Un utilisateur existe déjà avec cet email : " + normalizedEmail);
        }

        ServiceAdministratif service = null;
        if (request.getServiceId() != null && request.getServiceId() > 0) {
            service = serviceRepository.findById(request.getServiceId())
                    .orElseThrow(() -> BusinessException.notFound("Service administratif introuvable avec l'ID : " + request.getServiceId()));
        }

        String rawPassword = (request.getMotDePasse() != null && !request.getMotDePasse().isBlank())
                ? request.getMotDePasse()
                : "Civica@2026!";

        Utilisateur u = Utilisateur.builder()
                .email(normalizedEmail)
                .nom(request.getNom().trim())
                .prenom(request.getPrenom().trim())
                .telephone(request.getTelephone() != null && !request.getTelephone().isBlank() ? request.getTelephone().trim() : null)
                .role(request.getRole())
                .motDePasseHash(passwordEncoder.encode(rawPassword))
                .service(service)
                .actif(true)
                .emailVerifie(true)
                .build();

        return toAdminDto(utilisateurRepository.save(u));
    }

    @Transactional
    public UtilisateurAdminDto updateUser(Long userId, UpdateUserAdminRequest request) {
        Utilisateur u = utilisateurRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("Utilisateur non trouvé avec l'ID : " + userId));

        if (request.getRole() != null) {
            u.setRole(request.getRole());
        }

        if (request.getServiceId() != null) {
            if (request.getServiceId() <= 0) {
                u.setService(null);
            } else {
                ServiceAdministratif service = serviceRepository.findById(request.getServiceId())
                        .orElseThrow(() -> BusinessException.notFound("Service non trouvé avec l'ID : " + request.getServiceId()));
                u.setService(service);
            }
        } else if (request.getRole() == Role.ROLE_CITOYEN || request.getRole() == Role.ROLE_SUPER_ADMIN) {
            u.setService(null);
        }

        if (request.getActif() != null) {
            u.setActif(request.getActif());
        }

        return toAdminDto(utilisateurRepository.save(u));
    }

    @Transactional
    public UtilisateurAdminDto toggleUserStatus(Long userId) {
        Utilisateur u = utilisateurRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("Utilisateur non trouvé avec l'ID : " + userId));

        u.setActif(!Boolean.TRUE.equals(u.getActif()));
        return toAdminDto(utilisateurRepository.save(u));
    }

    @Transactional(readOnly = true)
    public List<Organisation> getAllOrganisations() {
        return organisationRepository.findAll();
    }

    @Transactional
    public Organisation createOrganisation(CreateOrganisationRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (organisationRepository.findByCode(code).isPresent()) {
            throw BusinessException.badRequest("Une organisation avec le code '" + code + "' existe déjà.");
        }

        Organisation org = Organisation.builder()
                .code(code)
                .nom(request.getNom().trim())
                .typeOrganisation(request.getTypeOrganisation().trim())
                .actif(true)
                .build();

        return organisationRepository.save(org);
    }

    @Transactional
    public Organisation toggleOrganisationStatus(Long orgId) {
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> BusinessException.notFound("Organisation non trouvée avec l'ID : " + orgId));
        org.setActif(!Boolean.TRUE.equals(org.getActif()));
        return organisationRepository.save(org);
    }

    @Transactional(readOnly = true)
    public List<ServiceAdminDto> getAllServices() {
        return serviceRepository.findAll().stream()
                .map(this::toServiceAdminDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ServiceAdminDto createService(CreateServiceRequest request) {
        Organisation org = organisationRepository.findById(request.getOrganisationId())
                .orElseThrow(() -> BusinessException.notFound("Organisation non trouvée avec l'ID : " + request.getOrganisationId()));

        String code = request.getCode().trim().toUpperCase();
        if (serviceRepository.findByOrganisationIdAndCode(org.getId(), code).isPresent()) {
            throw BusinessException.badRequest("Un service avec le code '" + code + "' existe déjà dans cette organisation.");
        }

        ServiceAdministratif serv = ServiceAdministratif.builder()
                .organisation(org)
                .code(code)
                .nom(request.getNom().trim())
                .actif(true)
                .build();

        return toServiceAdminDto(serviceRepository.save(serv));
    }

    @Transactional
    public ServiceAdminDto toggleServiceStatus(Long serviceId) {
        ServiceAdministratif serv = serviceRepository.findById(serviceId)
                .orElseThrow(() -> BusinessException.notFound("Service non trouvé avec l'ID : " + serviceId));
        serv.setActif(!Boolean.TRUE.equals(serv.getActif()));
        return toServiceAdminDto(serviceRepository.save(serv));
    }

    @Transactional(readOnly = true)
    public List<Demarche> getAllDemarches() {
        return demarcheRepository.findAll();
    }

    @Transactional
    public Demarche createDemarche(CreateDemarcheRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (demarcheRepository.findByCode(code).isPresent()) {
            throw BusinessException.badRequest("Une démarche avec le code '" + code + "' existe déjà.");
        }

        Demarche d = Demarche.builder()
                .code(code)
                .titre(request.getTitre().trim())
                .description(request.getDescription())
                .delaiIndicatifJours(request.getDelaiIndicatifJours() != null ? request.getDelaiIndicatifJours() : 7)
                .actif(true)
                .build();

        return demarcheRepository.save(d);
    }

    @Transactional
    public Demarche toggleDemarcheStatus(Long demarcheId) {
        Demarche d = demarcheRepository.findById(demarcheId)
                .orElseThrow(() -> BusinessException.notFound("Démarche non trouvée avec l'ID : " + demarcheId));
        d.setActif(!Boolean.TRUE.equals(d.getActif()));
        return demarcheRepository.save(d);
    }

    @Transactional(readOnly = true)
    public List<HistoriqueDossierDto> getRecentAuditLogs() {
        return historiqueRepository.findTop100ByOrderByTimestampDesc().stream()
                .map(citoyenService::toHistoriqueDto)
                .collect(Collectors.toList());
    }

    private UtilisateurAdminDto toAdminDto(Utilisateur u) {
        Long serviceId = u.getService() != null ? u.getService().getId() : null;
        String serviceNom = u.getService() != null ? u.getService().getNom() : null;
        String orgNom = (u.getService() != null && u.getService().getOrganisation() != null)
                ? u.getService().getOrganisation().getNom() : null;

        return UtilisateurAdminDto.builder()
                .id(u.getId())
                .email(u.getEmail())
                .nom(u.getNom())
                .prenom(u.getPrenom())
                .telephone(u.getTelephone())
                .role(u.getRole())
                .actif(u.getActif())
                .emailVerifie(u.getEmailVerifie())
                .serviceId(serviceId)
                .serviceNom(serviceNom)
                .organisationNom(orgNom)
                .createdAt(u.getCreatedAt())
                .build();
    }

    private ServiceAdminDto toServiceAdminDto(ServiceAdministratif s) {
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
