package cm.civica.backend.service;

import cm.civica.backend.dto.*;
import cm.civica.backend.enums.Role;
import cm.civica.backend.exception.BusinessException;
import cm.civica.backend.model.*;
import cm.civica.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired(required = false)
    private OrganisationTypeRepository organisationTypeRepository;

    @Autowired
    private ServiceAdministratifRepository serviceRepository;

    @Autowired
    private DemarcheRepository demarcheRepository;

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private FinanceService financeService;

    @Autowired(required = false)
    private EmailService emailService;

    @Autowired
    private PersonnelHierarchyHelper personnelHierarchyHelper;

    @Transactional(readOnly = true)
    public AdminDashboardStatsDto getDashboardStats() {
        List<Organisation> organisations = organisationRepository.findAll();
        List<ServiceAdministratif> services = serviceRepository.findAll();
        List<Demarche> demarches = demarcheRepository.findAll();

        long organisationsActives = organisations.stream().filter(o -> Boolean.TRUE.equals(o.getActif())).count();
        long servicesActifs = services.stream().filter(s -> Boolean.TRUE.equals(s.getActif())).count();
        long demarchesActives = demarches.stream().filter(d -> Boolean.TRUE.equals(d.getActif())).count();

        long totalUsers = utilisateurRepository.count();
        long totalCitoyens = utilisateurRepository.findByRole(Role.ROLE_CITOYEN).size();
        long totalAgents = utilisateurRepository.findByRole(Role.ROLE_AGENT).size();
        long totalSousResponsables = utilisateurRepository.findByRole(Role.ROLE_SOUS_RESPONSABLE).size();
        long totalResponsables = utilisateurRepository.findByRole(Role.ROLE_RESPONSABLE).size();

        Map<String, Long> organisationsParType = organisations.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getTypeOrganisation() != null ? o.getTypeOrganisation() : "AUTRE",
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        Map<String, Long> servicesParOrganisation = organisations.stream()
                .collect(Collectors.toMap(
                        Organisation::getNom,
                        o -> services.stream()
                                .filter(s -> s.getOrganisation() != null && o.getId().equals(s.getOrganisation().getId()))
                                .count(),
                        Long::sum,
                        LinkedHashMap::new
                ));

        return AdminDashboardStatsDto.builder()
                .totalOrganisations(organisations.size())
                .organisationsActives(organisationsActives)
                .totalServices(services.size())
                .servicesActifs(servicesActifs)
                .totalDemarches(demarches.size())
                .demarchesActives(demarchesActives)
                .totalUtilisateurs(totalUsers)
                .totalCitoyens(totalCitoyens)
                .totalAgents(totalAgents)
                .totalSousResponsables(totalSousResponsables)
                .totalResponsables(totalResponsables)
                .organisationsParType(organisationsParType)
                .servicesParOrganisation(servicesParOrganisation)
                .build();
    }

    @Transactional(readOnly = true)
    public List<UtilisateurAdminDto> getAllUsers() {
        return utilisateurRepository.findAll().stream()
                .map(UtilisateurAdminDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UtilisateurAdminDto lookupUserByEmail(String email) {
        String normalizedEmail = email != null ? email.trim().toLowerCase() : "";
        if (normalizedEmail.isBlank()) {
            throw BusinessException.badRequest("Veuillez saisir une adresse email à rechercher.");
        }

        Utilisateur user = utilisateurRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> BusinessException.notFound(
                        "Aucun compte CIVICA enregistré pour l'adresse email : " + normalizedEmail));

        return UtilisateurAdminDto.fromEntity(user);
    }

    @Transactional
    public UtilisateurAdminDto createUser(CreateUserAdminRequest request) {
        return personnelHierarchyHelper.createInternalUser(request);
    }

    @Transactional
    public UtilisateurAdminDto updateUser(Long userId, UpdateUserAdminRequest request) {
        Utilisateur u = utilisateurRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("Utilisateur non trouvé avec l'ID : " + userId));

        Role targetRole = request.getRole() != null ? request.getRole() : u.getRole();
        Long targetOrgId = request.getOrganisationId() != null
                ? (request.getOrganisationId() <= 0 ? null : request.getOrganisationId())
                : (u.getOrganisation() != null ? u.getOrganisation().getId() : null);
        Long targetServiceId = request.getServiceId() != null
                ? (request.getServiceId() <= 0 ? null : request.getServiceId())
                : (u.getService() != null ? u.getService().getId() : null);

        return personnelHierarchyHelper.applyUserUpdate(
                u, targetRole, targetOrgId, targetServiceId, request.getActif()
        );
    }

    @Transactional
    public UtilisateurAdminDto toggleUserStatus(Long userId) {
        Utilisateur u = utilisateurRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("Utilisateur non trouvé avec l'ID : " + userId));

        u.setActif(!Boolean.TRUE.equals(u.getActif()));
        return UtilisateurAdminDto.fromEntity(utilisateurRepository.save(u));
    }

    @Transactional(readOnly = true)
    public List<Organisation> getAllOrganisations() {
        return organisationRepository.findAll();
    }

    @Transactional
    public Organisation createOrganisation(CreateOrganisationRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (organisationRepository.findByCode(code).isPresent()) {
            throw BusinessException.conflict("Une organisation avec le code '" + code + "' existe déjà.");
        }

        Utilisateur responsable = null;
        if (request.getResponsableEmail() != null && !request.getResponsableEmail().isBlank()) {
            String normalizedEmail = request.getResponsableEmail().trim().toLowerCase();
            responsable = utilisateurRepository.findByEmailIgnoreCase(normalizedEmail)
                    .orElseThrow(() -> BusinessException.badRequest(
                            "Aucun compte CIVICA trouvé pour l'adresse email « " + normalizedEmail
                                    + " ». Le responsable doit disposer d'un compte CIVICA au préalable."));

            if (!Boolean.TRUE.equals(responsable.getActif())) {
                throw BusinessException.conflict(
                        "Le compte CIVICA de " + responsable.getPrenom() + " " + responsable.getNom()
                                + " (" + responsable.getEmail() + ") est désactivé et ne peut pas être nommé responsable.");
            }
            if (responsable.getRole() == Role.ROLE_SUPER_ADMIN) {
                throw BusinessException.conflict(
                        "Un Administrateur Principal (ROLE_SUPER_ADMIN) ne peut pas être nommé responsable d'organisation.");
            }
            if (responsable.getRole() == Role.ROLE_RESPONSABLE && responsable.getOrganisation() != null) {
                throw BusinessException.conflict(
                        "Cet utilisateur est déjà Responsable de l'organisation « "
                                + responsable.getOrganisation().getNom()
                                + " ». Veuillez choisir un autre compte CIVICA.");
            }
        }

        Organisation org = Organisation.builder()
                .code(code)
                .nom(request.getNom().trim())
                .typeOrganisation(request.getTypeOrganisation().trim().toUpperCase(Locale.ROOT))
                .actif(true)
                .build();

        Organisation saved = organisationRepository.save(org);
        if (financeService != null) {
            financeService.ensureWalletForOrganisation(saved);
        }

        if (responsable != null) {
            personnelHierarchyHelper.applyUserUpdate(responsable, Role.ROLE_RESPONSABLE, saved.getId(), null, null);
            if (emailService != null) {
                emailService.sendOrganisationResponsableNotification(responsable, saved);
            }
        }
        return saved;
    }

    @Transactional
    public Organisation updateOrganisation(Long orgId, UpdateOrganisationRequest request) {
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> BusinessException.notFound("Organisation non trouvée avec l'ID : " + orgId));

        String code = request.getCode().trim().toUpperCase();
        organisationRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(orgId))
                .ifPresent(existing -> {
                    throw BusinessException.conflict("Une autre organisation utilise déjà le code '" + code + "'.");
                });

        org.setCode(code);
        org.setNom(request.getNom().trim());
        org.setTypeOrganisation(request.getTypeOrganisation().trim().toUpperCase(Locale.ROOT));
        if (request.getActif() != null) {
            org.setActif(request.getActif());
        }

        Organisation saved = organisationRepository.save(org);
        if (financeService != null) {
            financeService.ensureWalletForOrganisation(saved);
        }
        return saved;
    }

    @Transactional
    public void deleteOrganisation(Long orgId) {
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> BusinessException.notFound("Organisation non trouvée avec l'ID : " + orgId));

        List<ServiceAdministratif> services = serviceRepository.findByOrganisationId(orgId);
        if (!services.isEmpty()) {
            throw BusinessException.conflict("Impossible de supprimer l'organisation '" + org.getNom()
                    + "' : " + services.size() + " service(s) administratif(s) y sont encore rattaché(s).");
        }

        List<Utilisateur> responsables = utilisateurRepository.findByOrganisationId(orgId);
        if (!responsables.isEmpty()) {
            throw BusinessException.conflict("Impossible de supprimer l'organisation '" + org.getNom()
                    + "' : un responsable y est encore rattaché.");
        }

        organisationRepository.delete(org);
        organisationRepository.flush();
    }

    @Transactional
    public Organisation toggleOrganisationStatus(Long orgId) {
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> BusinessException.notFound("Organisation non trouvée avec l'ID : " + orgId));
        org.setActif(!Boolean.TRUE.equals(org.getActif()));
        Organisation saved = organisationRepository.save(org);
        if (financeService != null) {
            financeService.ensureWalletForOrganisation(saved);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<OrganisationType> getAllOrganisationTypes() {
        return organisationTypeRepository.findAllByOrderByLibelleAsc();
    }

    @Transactional
    public OrganisationType createOrganisationType(CreateOrganisationTypeRequest request) {
        String libelle = request.getLibelle().trim();
        String code = normalizeOrganisationTypeCode(request.getCode(), libelle);

        if (organisationTypeRepository.existsByCodeIgnoreCase(code)) {
            throw BusinessException.conflict("Un type d'organisation avec le code '" + code + "' existe déjà.");
        }

        String description = (request.getDescription() != null && !request.getDescription().isBlank())
                ? request.getDescription().trim()
                : null;

        OrganisationType orgType = OrganisationType.builder()
                .code(code)
                .libelle(libelle)
                .description(description)
                .actif(true)
                .build();

        return organisationTypeRepository.save(orgType);
    }

    @Transactional
    public OrganisationType updateOrganisationType(Long id, UpdateOrganisationTypeRequest request) {
        OrganisationType orgType = organisationTypeRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Type d'organisation non trouvé avec l'ID : " + id));

        orgType.setLibelle(request.getLibelle().trim());
        orgType.setDescription(request.getDescription() != null && !request.getDescription().isBlank()
                ? request.getDescription().trim()
                : null);
        if (request.getActif() != null) {
            orgType.setActif(request.getActif());
        }
        return organisationTypeRepository.save(orgType);
    }

    @Transactional
    public OrganisationType toggleOrganisationTypeStatus(Long id) {
        OrganisationType orgType = organisationTypeRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Type d'organisation non trouvé avec l'ID : " + id));
        orgType.setActif(!Boolean.TRUE.equals(orgType.getActif()));
        return organisationTypeRepository.save(orgType);
    }

    @Transactional
    public void deleteOrganisationType(Long id) {
        OrganisationType orgType = organisationTypeRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Type d'organisation non trouvé avec l'ID : " + id));

        long count = organisationRepository.countByTypeOrganisationIgnoreCase(orgType.getCode());
        if (count > 0) {
            throw BusinessException.conflict(
                    "Impossible de supprimer le type '" + orgType.getLibelle() + "' : "
                            + count + " organisation(s) l'utilisent encore.");
        }

        organisationTypeRepository.delete(orgType);
        organisationTypeRepository.flush();
    }

    private String normalizeOrganisationTypeCode(String rawCode, String libelle) {
        String source = (rawCode != null && !rawCode.isBlank()) ? rawCode : libelle;
        if (source == null || source.isBlank()) {
            throw BusinessException.badRequest("Le code ou le libellé du type d'organisation est obligatoire.");
        }
        String decomposed = Normalizer.normalize(source.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String slug = decomposed.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.isBlank()) {
            throw BusinessException.badRequest(
                    "Le code du type d'organisation doit contenir au moins une lettre ou un chiffre.");
        }
        return slug.length() > 50 ? slug.substring(0, 50) : slug;
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
            throw BusinessException.conflict("Un service avec le code '" + code + "' existe déjà dans cette organisation.");
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
    public ServiceAdminDto updateService(Long serviceId, UpdateServiceRequest request) {
        ServiceAdministratif serv = serviceRepository.findById(serviceId)
                .orElseThrow(() -> BusinessException.notFound("Service non trouvé avec l'ID : " + serviceId));

        Organisation targetOrg = serv.getOrganisation();
        if (request.getOrganisationId() != null && request.getOrganisationId() > 0
                && !request.getOrganisationId().equals(targetOrg.getId())) {
            targetOrg = organisationRepository.findById(request.getOrganisationId())
                    .orElseThrow(() -> BusinessException.notFound("Organisation non trouvée avec l'ID : " + request.getOrganisationId()));
        }

        String code = request.getCode().trim().toUpperCase();
        serviceRepository.findByOrganisationIdAndCode(targetOrg.getId(), code)
                .filter(existing -> !existing.getId().equals(serviceId))
                .ifPresent(existing -> {
                    throw BusinessException.conflict("Un service avec le code '" + code + "' existe déjà dans cette organisation.");
                });

        serv.setOrganisation(targetOrg);
        serv.setCode(code);
        serv.setNom(request.getNom().trim());
        if (request.getActif() != null) {
            serv.setActif(request.getActif());
        }

        return toServiceAdminDto(serviceRepository.save(serv));
    }

    @Transactional
    public void deleteService(Long serviceId) {
        ServiceAdministratif serv = serviceRepository.findById(serviceId)
                .orElseThrow(() -> BusinessException.notFound("Service non trouvé avec l'ID : " + serviceId));

        List<Utilisateur> users = utilisateurRepository.findByServiceId(serviceId);
        if (!users.isEmpty()) {
            throw BusinessException.conflict("Impossible de supprimer le service '" + serv.getNom()
                    + "' : " + users.size() + " utilisateur(s) y sont encore affecté(s).");
        }

        if (dossierRepository.countByServiceCourantId(serviceId) > 0) {
            throw BusinessException.conflict("Impossible de supprimer le service '" + serv.getNom()
                    + "' : des dossiers administratifs y sont encore rattachés. Désactivez-le plutôt.");
        }

        serviceRepository.delete(serv);
        serviceRepository.flush();
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
            throw BusinessException.conflict("Une démarche avec le code '" + code + "' existe déjà.");
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
    public Demarche updateDemarche(Long demarcheId, UpdateDemarcheRequest request) {
        Demarche d = demarcheRepository.findById(demarcheId)
                .orElseThrow(() -> BusinessException.notFound("Démarche non trouvée avec l'ID : " + demarcheId));

        String code = request.getCode().trim().toUpperCase();
        demarcheRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(demarcheId))
                .ifPresent(existing -> {
                    throw BusinessException.conflict("Une autre démarche utilise déjà le code '" + code + "'.");
                });

        d.setCode(code);
        d.setTitre(request.getTitre().trim());
        d.setDescription(request.getDescription());
        if (request.getDelaiIndicatifJours() != null) {
            d.setDelaiIndicatifJours(request.getDelaiIndicatifJours());
        }
        if (request.getActif() != null) {
            d.setActif(request.getActif());
        }

        return demarcheRepository.save(d);
    }

    @Transactional
    public void deleteDemarche(Long demarcheId) {
        Demarche d = demarcheRepository.findById(demarcheId)
                .orElseThrow(() -> BusinessException.notFound("Démarche non trouvée avec l'ID : " + demarcheId));

        if (dossierRepository.countByDemarcheId(demarcheId) > 0) {
            throw BusinessException.conflict("Impossible de supprimer la démarche '" + d.getTitre()
                    + "' : elle est déjà associée à des dossiers. Désactivez-la plutôt.");
        }

        demarcheRepository.delete(d);
        demarcheRepository.flush();
    }

    @Transactional
    public Demarche toggleDemarcheStatus(Long demarcheId) {
        Demarche d = demarcheRepository.findById(demarcheId)
                .orElseThrow(() -> BusinessException.notFound("Démarche non trouvée avec l'ID : " + demarcheId));
        d.setActif(!Boolean.TRUE.equals(d.getActif()));
        return demarcheRepository.save(d);
    }

    private ServiceAdminDto toServiceAdminDto(ServiceAdministratif s) {
        return personnelHierarchyHelper.toServiceAdminDto(s);
    }
}
