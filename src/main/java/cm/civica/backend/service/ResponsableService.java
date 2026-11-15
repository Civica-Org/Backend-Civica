package cm.civica.backend.service;

import cm.civica.backend.dto.*;
import cm.civica.backend.enums.Role;
import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.exception.BusinessException;
import cm.civica.backend.model.Dossier;
import cm.civica.backend.model.ServiceAdministratif;
import cm.civica.backend.model.Utilisateur;
import cm.civica.backend.pattern.chain.CircuitValidationDossier;
import cm.civica.backend.pattern.chain.ResultatValidation;
import cm.civica.backend.pattern.observer.DossierEvent;
import cm.civica.backend.pattern.observer.DossierSubject;
import cm.civica.backend.pattern.state.DossierStateMachine;
import cm.civica.backend.repository.DossierRepository;
import cm.civica.backend.repository.HistoriqueDossierRepository;
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
 * Service dédié au rôle Responsable (Tâche B7).
 * Fournit les fonctionnalités de supervision, d'analyse des KPIs,
 * de réassignation d'instructeur et de validation hiérarchique.
 */
@Service
public class ResponsableService {

    private static final Logger log = LoggerFactory.getLogger(ResponsableService.class);

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

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

    /**
     * Calcule l'ensemble des indicateurs de supervision pour le tableau de bord du Responsable.
     */
    @Transactional(readOnly = true)
    public DashboardResponsableDto getDashboard() {
        Utilisateur responsable = authService.getCurrentUser();
        ServiceAdministratif service = responsable.getService();

        List<Dossier> dossiers;
        if (responsable.getRole() == Role.ROLE_SUPER_ADMIN || service == null) {
            dossiers = dossierRepository.findAll();
        } else {
            dossiers = dossierRepository.findByServiceCourantId(service.getId());
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

        // Charge des agents du service
        List<AgentChargeDto> agentsCharge = getAgentsCharge(service != null ? service.getId() : null);

        return DashboardResponsableDto.builder()
                .codeService(service != null ? service.getCode() : "TOUS")
                .nomService(service != null ? service.getNom() : "Tous les services (Vue Super Admin)")
                .nomOrganisation(service != null && service.getOrganisation() != null ? service.getOrganisation().getNom() : "République du Cameroun")
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
     * Liste des agents avec leur charge de travail.
     */
    @Transactional(readOnly = true)
    public List<AgentChargeDto> getAgentsCharge(Long serviceId) {
        List<Utilisateur> agents;
        if (serviceId != null) {
            agents = utilisateurRepository.findByServiceId(serviceId);
        } else {
            agents = utilisateurRepository.findByRole(Role.ROLE_AGENT);
        }

        return agents.stream().map(agent -> {
            long enCours = historiqueRepository.countByAgentIdAndNouveauStatut(agent.getId(), StatutDossier.EN_VERIFICATION);
            long totalTraite = historiqueRepository.countByAgentId(agent.getId());

            return AgentChargeDto.builder()
                    .agentId(agent.getId())
                    .nomComplet(agent.getPrenom() + " " + agent.getNom())
                    .email(agent.getEmail())
                    .telephone(agent.getTelephone())
                    .dossiersEnCours(enCours)
                    .dossiersTraitesTotal(totalTraite)
                    .actif(Boolean.TRUE.equals(agent.getActif()))
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Réassigne un dossier à un agent spécifique du service (décision du responsable).
     */
    @Transactional
    public DossierResponseDto reassignerDossier(String numeroSuivi, ReassignationDossierRequest request) {
        Utilisateur responsable = authService.getCurrentUser();
        Dossier dossier = dossierRepository.findByNumeroSuivi(numeroSuivi)
                .orElseThrow(() -> BusinessException.notFound("Dossier introuvable : " + numeroSuivi));

        Utilisateur agentCible = utilisateurRepository.findById(request.getAgentId())
                .orElseThrow(() -> BusinessException.notFound("Agent cible introuvable : " + request.getAgentId()));

        if (agentCible.getRole() != Role.ROLE_AGENT && agentCible.getRole() != Role.ROLE_RESPONSABLE) {
            throw BusinessException.badRequest("L'utilisateur cible n'a pas le profil agent ou responsable.");
        }

        StatutDossier ancienStatut = dossier.getStatut();
        dossier.setStatut(StatutDossier.EN_VERIFICATION);
        dossier.setUpdatedAt(Instant.now());
        Dossier saved = dossierRepository.save(dossier);

        String note = request.getNoteResponsable() != null ? request.getNoteResponsable() : "Réassignation par le responsable";

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
     * Validation hiérarchique de second niveau par le Responsable.
     */
    @Transactional
    public DossierResponseDto validerHierarchique(String numeroSuivi, DecisionDossierRequest request) {
        Utilisateur responsable = authService.getCurrentUser();
        Dossier dossier = dossierRepository.findByNumeroSuiviWithPiecesAndDemarche(numeroSuivi)
                .orElseThrow(() -> BusinessException.notFound("Dossier introuvable : " + numeroSuivi));

        StatutDossier ancienStatut = dossier.getStatut();

        if (request.getStatut() == StatutDossier.VALIDE) {
            ResultatValidation res = circuitValidation.executerCircuit(dossier);
            if (!res.isValide()) {
                throw BusinessException.badRequest("Validation impossible : " + res.getMotifRejet());
            }
            stateMachine.valider(dossier);
        } else if (request.getStatut() == StatutDossier.REJETE) {
            stateMachine.rejeter(dossier, request.getMotifRejet());
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
}
