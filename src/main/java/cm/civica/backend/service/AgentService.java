package cm.civica.backend.service;

import cm.civica.backend.dto.AuditCompletudeDto;
import cm.civica.backend.dto.DecisionDossierRequest;
import cm.civica.backend.dto.DossierResponseDto;
import cm.civica.backend.dto.TransfertDossierRequest;
import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.exception.BusinessException;
import cm.civica.backend.model.Dossier;
import cm.civica.backend.model.HistoriqueDossier;
import cm.civica.backend.model.ServiceAdministratif;
import cm.civica.backend.model.Utilisateur;
import cm.civica.backend.pattern.adapter.LlmAuditeurService;
import cm.civica.backend.pattern.chain.CircuitValidationDossier;
import cm.civica.backend.pattern.chain.ResultatValidation;
import cm.civica.backend.pattern.observer.DossierEvent;
import cm.civica.backend.pattern.observer.DossierSubject;
import cm.civica.backend.pattern.state.DossierStateMachine;
import cm.civica.backend.repository.DossierRepository;
import cm.civica.backend.repository.HistoriqueDossierRepository;
import cm.civica.backend.repository.ServiceAdministratifRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgentService {

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private ServiceAdministratifRepository serviceRepository;

    @Autowired
    private HistoriqueDossierRepository historiqueRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private CitoyenService citoyenService;

    @Autowired
    private EmailService emailService;

    // Pattern GoF : State
    @Autowired
    private DossierStateMachine stateMachine;

    // Pattern GoF : Chain of Responsibility
    @Autowired
    private CircuitValidationDossier circuitValidation;

    // Pattern GoF : Adapter (LLM)
    @Autowired
    private LlmAuditeurService llmAuditeurService;

    // Pattern GoF : Observer
    @Autowired
    private DossierSubject dossierSubject;

    @Transactional(readOnly = true)
    public List<DossierResponseDto> getDossiersAInstructionner(StatutDossier statut) {
        Utilisateur agent = authService.getCurrentUser();

        List<Dossier> dossiers;
        if (agent.getService() != null) {
            if (statut != null) {
                dossiers = dossierRepository.findByServiceCourantIdAndStatut(agent.getService().getId(), statut);
            } else {
                dossiers = dossierRepository.findByServiceCourantId(agent.getService().getId());
            }
        } else {
            if (statut != null) {
                dossiers = dossierRepository.findByStatut(statut);
            } else {
                dossiers = dossierRepository.findAll();
            }
        }

        return dossiers.stream()
                .map(citoyenService::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public DossierResponseDto prendreEnCharge(String numeroSuivi) {
        Utilisateur agent = authService.getCurrentUser();
        Dossier dossier = dossierRepository.findByNumeroSuivi(numeroSuivi)
                .orElseThrow(() -> BusinessException.notFound("Dossier introuvable : " + numeroSuivi));

        StatutDossier ancienStatut = dossier.getStatut();

        // Transition d'état sécurisée via le Pattern State
        stateMachine.prendreEnCharge(dossier);
        Dossier saved = dossierRepository.save(dossier);

        // Notification découplée via le Pattern Observer (audit, email, télémétrie)
        dossierSubject.notifyObservers(DossierEvent.of(
                saved, ancienStatut, StatutDossier.EN_VERIFICATION,
                "PRISE_EN_CHARGE",
                "Prise en charge pour instruction par l'agent " + agent.getPrenom() + " " + agent.getNom(),
                agent, dossier.getServiceCourant()
        ));

        return citoyenService.toDto(saved);
    }

    @Transactional
    public DossierResponseDto rendreDecision(String numeroSuivi, DecisionDossierRequest request) {
        Utilisateur agent = authService.getCurrentUser();
        Dossier dossier = dossierRepository.findByNumeroSuiviWithPiecesAndDemarche(numeroSuivi)
                .orElseThrow(() -> BusinessException.notFound("Dossier introuvable : " + numeroSuivi));

        StatutDossier ancienStatut = dossier.getStatut();

        if (request.getStatut() == StatutDossier.VALIDE) {
            // Exécution de la chaîne de validation (Pattern Chain of Responsibility)
            ResultatValidation res = circuitValidation.executerCircuit(dossier);
            if (!res.isValide()) {
                throw BusinessException.badRequest("Validation impossible : " + res.getMotifRejet());
            }
            stateMachine.valider(dossier);
        } else if (request.getStatut() == StatutDossier.REJETE) {
            stateMachine.rejeter(dossier, request.getMotifRejet());
        } else {
            dossier.setStatut(request.getStatut());
        }

        dossier.setUpdatedAt(Instant.now());
        Dossier saved = dossierRepository.save(dossier);

        String motifOuCom = request.getCommentaire() != null ? request.getCommentaire() : request.getMotifRejet();

        // Notification découplée via le Pattern Observer (audit, email, métriques)
        dossierSubject.notifyObservers(DossierEvent.of(
                saved, ancienStatut, request.getStatut(),
                request.getStatut() == StatutDossier.VALIDE ? "VALIDATION" : "REJET",
                motifOuCom,
                agent, dossier.getServiceCourant()
        ));

        return citoyenService.toDto(saved);
    }

    @Transactional
    public DossierResponseDto transfererDossier(String numeroSuivi, TransfertDossierRequest request) {
        Utilisateur agent = authService.getCurrentUser();
        Dossier dossier = dossierRepository.findByNumeroSuivi(numeroSuivi)
                .orElseThrow(() -> BusinessException.notFound("Dossier introuvable : " + numeroSuivi));

        ServiceAdministratif cible = serviceRepository.findById(request.getServiceCibleId())
                .orElseThrow(() -> BusinessException.notFound("Service cible introuvable avec l'ID : " + request.getServiceCibleId()));

        ServiceAdministratif source = dossier.getServiceCourant();
        StatutDossier ancienStatut = dossier.getStatut();

        // Pattern State + Invariant Alloy : transfert atomique
        stateMachine.transferer(dossier, cible);
        Dossier saved = dossierRepository.save(dossier);

        String commentaire = request.getCommentaire() != null ? request.getCommentaire()
                : "Transfert vers " + cible.getNom();

        // Notification découplée via le Pattern Observer (audit, email, traçabilité)
        dossierSubject.notifyObservers(DossierEvent.of(
                saved, ancienStatut, StatutDossier.TRANSFERE,
                "TRANSFERT_INTER_SERVICE",
                commentaire,
                agent, cible
        ));

        return citoyenService.toDto(saved);
    }

    @Transactional(readOnly = true)
    public AuditCompletudeDto auditerDossierParIa(String numeroSuivi) {
        Dossier dossier = dossierRepository.findByNumeroSuiviWithPiecesAndDemarche(numeroSuivi)
                .orElseThrow(() -> BusinessException.notFound("Dossier introuvable : " + numeroSuivi));

        // Pattern Adapter : appel du service d'audit LLM
        return llmAuditeurService.auditerDossier(dossier);
    }

    @Transactional(readOnly = true)
    public String suggererMotifRejetParIa(String numeroSuivi, String contexte) {
        Dossier dossier = dossierRepository.findByNumeroSuiviWithPiecesAndDemarche(numeroSuivi)
                .orElseThrow(() -> BusinessException.notFound("Dossier introuvable : " + numeroSuivi));

        // Pattern Adapter : suggestion de motif de rejet
        return llmAuditeurService.suggererMotifRejet(dossier, contexte);
    }
}
