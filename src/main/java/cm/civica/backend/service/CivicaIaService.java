package cm.civica.backend.service;

import cm.civica.backend.dto.AuditCompletudeDto;
import cm.civica.backend.dto.ia.*;
import cm.civica.backend.enums.Role;
import cm.civica.backend.exception.BusinessException;
import cm.civica.backend.model.*;
import cm.civica.backend.pattern.adapter.GeminiRestClient;
import cm.civica.backend.pattern.adapter.LlmAuditeurService;
import cm.civica.backend.pattern.adapter.PiiAnonymizer;
import cm.civica.backend.repository.AiSuggestionRepository;
import cm.civica.backend.repository.DemarcheRepository;
import cm.civica.backend.repository.DossierRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service applicatif centralisant "Civica IA" pour toutes les interfaces Web & Mobile :
 * - Statut du fournisseur LLM (Gemini gratuit / Repli déterministe)
 * - Pré-contrôle citoyen avant dépôt
 * - Audit de complétude et reformulation pédagogique pour agents
 * - Assistant conversationnel contextuel sécurisé et anonymisé
 * - Traçabilité complète dans la table ai_suggestions (Chapitre 8)
 */
@Service
public class CivicaIaService {

    private static final Logger log = LoggerFactory.getLogger(CivicaIaService.class);

    @Autowired
    private LlmAuditeurService llmAuditeurService;

    @Autowired
    private GeminiRestClient geminiRestClient;

    @Autowired
    private PiiAnonymizer piiAnonymizer;

    @Autowired
    private AiSuggestionRepository aiSuggestionRepository;

    @Autowired
    private DemarcheRepository demarcheRepository;

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private PersonnelHierarchyHelper personnelHierarchyHelper;

    @Autowired
    private ObjectMapper objectMapper;

    public IaStatusDto getStatus() {
        boolean configured = geminiRestClient.isConfigured();
        return IaStatusDto.builder()
                .geminiConfigure(configured)
                .fournisseurActif(configured ? "GEMINI_REST" : "FALLBACK_DETERMINISTE")
                .modelePrincipal(geminiRestClient.getPrimaryModel())
                .modeleFallback(geminiRestClient.getFallbackModel())
                .timeoutMs(geminiRestClient.getTimeoutMs())
                .regleCardinale("Assistance strictement consultative : la décision administrative appartient exclusivement à l'agent ou officier humain habilité.")
                .build();
    }

    @Transactional
    public AuditCompletudeDto precheckCompletude(PrecheckCompletudeRequest request) {
        Utilisateur caller = authService.getCurrentUserOptional().orElse(null);
        String code = request.getDemarcheCode().trim().toUpperCase();
        String titre = demarcheRepository.findByCode(code)
                .map(Demarche::getTitre)
                .orElse(code);

        AuditCompletudeDto result = llmAuditeurService.precheckPieces(
                code,
                titre,
                request.getVarianteCircuit(),
                request.getIntitulesPieces()
        );

        AiSuggestion logged = saveSuggestion(
                null,
                "PRECHECK_DEPOT",
                result.getSourceProvider(),
                result.getModeleUtilise(),
                "Pré-contrôle " + code + " (" + (request.getIntitulesPieces() != null ? request.getIntitulesPieces().size() : 0) + " pièce(s))",
                toJsonSafe(result),
                result.isModeDegrade(),
                caller,
                result.getLatenceMs()
        );
        result.setSuggestionId(logged.getId());
        return result;
    }

    @Transactional
    public AuditCompletudeDto auditerDossierPourStaff(String numeroSuivi) {
        Utilisateur staff = authService.getCurrentUser();
        Dossier dossier = dossierRepository.findByNumeroSuiviWithPiecesAndDemarche(numeroSuivi.trim())
                .orElseThrow(() -> BusinessException.notFound("Dossier introuvable : " + numeroSuivi));
        if (staff.getRole() != Role.ROLE_SUPER_ADMIN) {
            personnelHierarchyHelper.verifyDossierAccess(staff, dossier);
        }
        return auditerDossierPourAgent(dossier, staff);
    }

    @Transactional
    public AuditCompletudeDto auditerDossierPourAgent(Dossier dossier, Utilisateur agent) {
        AuditCompletudeDto result = llmAuditeurService.auditerDossier(dossier);
        AiSuggestion logged = saveSuggestion(
                dossier,
                "COMPLETUDE_AUDIT",
                result.getSourceProvider(),
                result.getModeleUtilise(),
                "Audit complétude dossier [DOSSIER_ANONYME] · " +
                        (dossier.getDemarche() != null ? dossier.getDemarche().getCode() : "DEM"),
                toJsonSafe(result),
                result.isModeDegrade(),
                agent,
                result.getLatenceMs()
        );
        result.setSuggestionId(logged.getId());
        return result;
    }

    @Transactional
    public ReformulateRejectionResponse reformulerMotifRejet(
            String numeroSuivi,
            ReformulateRejectionRequest request
    ) {
        Utilisateur agent = authService.getCurrentUser();
        Dossier dossier = dossierRepository.findByNumeroSuiviWithPiecesAndDemarche(numeroSuivi.trim())
                .orElseThrow(() -> BusinessException.notFound("Dossier introuvable : " + numeroSuivi));
        personnelHierarchyHelper.verifyDossierAccess(agent, dossier);

        ReformulateRejectionResponse response = llmAuditeurService.reformulerMotifCourtois(
                dossier,
                request != null ? request.getMotifTechnique() : null,
                request != null ? request.getContexte() : null
        );

        AiSuggestion logged = saveSuggestion(
                dossier,
                "REFORMULATION_REJET",
                response.getSourceProvider(),
                response.getModeleUtilise(),
                piiAnonymizer.sanitizeText(
                        request != null ? request.getMotifTechnique() : "Suggestion rejet",
                        dossier
                ),
                toJsonSafe(response),
                response.isModeDegrade(),
                agent,
                response.getLatenceMs()
        );
        response.setSuggestionId(logged.getId());
        return response;
    }

    @Transactional
    public CivicaIaChatResponse chat(CivicaIaChatRequest request) {
        Utilisateur caller = authService.getCurrentUserOptional().orElse(null);
        Dossier dossierContext = null;

        if (request.getNumeroSuivi() != null && !request.getNumeroSuivi().isBlank()) {
            dossierContext = dossierRepository
                    .findByNumeroSuiviWithPiecesAndDemarche(request.getNumeroSuivi().trim())
                    .orElse(null);

            // Cloisonnement RBAC si staff authentifié : un agent ne doit consulter qu'un dossier de son périmètre
            if (dossierContext != null && caller != null
                    && caller.getRole() != Role.ROLE_CITOYEN
                    && caller.getRole() != Role.ROLE_SUPER_ADMIN) {
                personnelHierarchyHelper.verifyDossierAccess(caller, dossierContext);
            }
        }

        String sanitizedQuestion = piiAnonymizer.sanitizeText(request.getQuestion(), dossierContext);
        String roleContext = describeCallerRole(caller);
        String groundingContext = buildGroundingContext(dossierContext, request.getDemarcheCode(), caller);

        CivicaIaChatResponse response = llmAuditeurService.repondreAssistant(
                sanitizedQuestion,
                roleContext,
                groundingContext,
                request.getPageContexte(),
                request.getHistorique(),
                dossierContext,
                caller
        );

        saveSuggestion(
                dossierContext,
                "ASSISTANT_CHAT",
                response.getSourceProvider(),
                response.getModeleUtilise(),
                sanitizedQuestion,
                toJsonSafe(response),
                response.isModeDegrade(),
                caller,
                response.getLatenceMs()
        );

        return response;
    }

    @Transactional
    public String enregistrerSuggestionRejetAgent(Dossier dossier, String contexte, Utilisateur agent) {
        String suggestion = llmAuditeurService.suggererMotifRejet(dossier, contexte);
        saveSuggestion(
                dossier,
                "REFORMULATION_REJET",
                geminiRestClient.isConfigured() ? "GEMINI_REST" : "FALLBACK_DETERMINISTE",
                geminiRestClient.isConfigured() ? geminiRestClient.getPrimaryModel() : "rule-based-v1",
                piiAnonymizer.sanitizeText(contexte, dossier),
                suggestion,
                !geminiRestClient.isConfigured(),
                agent,
                null
        );
        return suggestion;
    }

    @Transactional
    public void linkFinalDecisionValidator(Dossier dossier, Utilisateur validateur) {
        if (dossier == null || dossier.getId() == null || validateur == null) return;
        List<AiSuggestion> list = aiSuggestionRepository.findByDossierIdOrderByCreatedAtDesc(dossier.getId());
        for (AiSuggestion suggestion : list) {
            if (suggestion.getValidateur() == null) {
                suggestion.setValidateur(validateur);
                aiSuggestionRepository.save(suggestion);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<AiSuggestionDto> getRecentSuggestions() {
        Utilisateur caller = authService.getCurrentUser();
        List<AiSuggestion> rows = aiSuggestionRepository.findTop50ByOrderByCreatedAtDesc();

        return rows.stream()
                .filter(s -> isSuggestionVisibleTo(caller, s))
                .map(this::toSuggestionDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public AiSuggestionDto recordFeedback(Long suggestionId, boolean accepted) {
        Utilisateur staff = authService.getCurrentUser();
        AiSuggestion suggestion = aiSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> BusinessException.notFound("Suggestion IA introuvable : " + suggestionId));

        if (!isSuggestionVisibleTo(staff, suggestion)) {
            throw BusinessException.forbidden("Accès refusé à cette trace d'audit IA.");
        }

        suggestion.setAccepteeParAgent(accepted);
        if (suggestion.getValidateur() == null) {
            suggestion.setValidateur(staff);
        }
        return toSuggestionDto(aiSuggestionRepository.save(suggestion));
    }

    private boolean isSuggestionVisibleTo(Utilisateur caller, AiSuggestion suggestion) {
        if (caller.getRole() == Role.ROLE_SUPER_ADMIN) {
            return true;
        }
        if (suggestion.getDossier() == null) {
            return suggestion.getDemandeur() != null && suggestion.getDemandeur().getId().equals(caller.getId());
        }
        ServiceAdministratif dossierSrv = suggestion.getDossier().getServiceCourant();
        if (caller.getRole() == Role.ROLE_RESPONSABLE) {
            Organisation userOrg = caller.resolveEffectiveOrganisation();
            return userOrg != null && dossierSrv != null && dossierSrv.getOrganisation() != null
                    && userOrg.getId().equals(dossierSrv.getOrganisation().getId());
        }
        if (caller.getRole() == Role.ROLE_SOUS_RESPONSABLE || caller.getRole() == Role.ROLE_AGENT) {
            return caller.getService() != null && dossierSrv != null
                    && caller.getService().getId().equals(dossierSrv.getId());
        }
        return false;
    }

    private String describeCallerRole(Utilisateur caller) {
        if (caller == null) {
            return "Citoyen / Visiteur public non connecté";
        }
        return switch (caller.getRole()) {
            case ROLE_CITOYEN -> "Citoyen connecté";
            case ROLE_AGENT -> "Agent instructeur administratif";
            case ROLE_SOUS_RESPONSABLE -> "Sous-Responsable / Chef de Service administratif";
            case ROLE_RESPONSABLE -> "Responsable / Directeur d'Organisation administrative";
            case ROLE_SUPER_ADMIN -> "Super Administrateur technique CIVICA";
        };
    }

    private String buildGroundingContext(Dossier dossier, String demarcheCode, Utilisateur caller) {
        StringBuilder sb = new StringBuilder();
        sb.append("Démarches actives dans CIVICA :\n");
        List<Demarche> demarches = demarcheRepository.findByActifTrue();
        for (Demarche d : demarches) {
            sb.append(String.format("- %s (%s), délai indicatif %d jours : %s\n",
                    d.getCode(), d.getTitre(), d.getDelaiIndicatifJours(), d.getDescription()));
        }

        sb.append("\nRègles tarifaires officielles (FCFA / XAF) :\n");
        sb.append("- DEM-CNI : 10 000 FCFA (70% DGSN, 30% Commune d'enrôlement)\n");
        sb.append("- DEM-ACTE-NAISS : NORMALE <=90j = 0 FCFA ; EXTRAIT_COPIE = 1 000 FCFA (100% Commune) ; TARDIVE 91-365j = 1 500 FCFA (60% Commune, 40% TPI) ; JUDICIAIRE >365j = 5 000 FCFA (70% TPI, 30% Commune)\n");
        sb.append("- DEM-CASIER-JUD : 2 500 FCFA (80% TPI, 20% DGSN)\n");
        sb.append("- Double validation financière : Tout retrait financier institutionnel requiert 2 responsables distincts (initiateur != approbateur).\n");

        if (demarcheCode != null && !demarcheCode.isBlank()) {
            sb.append("\nDémarche sélectionnée à l'écran : ").append(demarcheCode.trim().toUpperCase()).append("\n");
        }

        if (dossier != null) {
            sb.append("\nContexte anonymisé du dossier consulté :\n");
            sb.append("- Code démarche : ").append(dossier.getDemarche() != null ? dossier.getDemarche().getCode() : "N/A").append("\n");
            sb.append("- Statut actuel automate : ").append(dossier.getStatut()).append("\n");
            sb.append("- Service détenteur : ").append(dossier.getServiceCourant() != null ? dossier.getServiceCourant().getNom() : "Aucun").append("\n");
            if (dossier.getMotifRejet() != null && !dossier.getMotifRejet().isBlank()) {
                sb.append("- Motif rejet anonymisé : ").append(piiAnonymizer.sanitizeText(dossier.getMotifRejet(), dossier)).append("\n");
            }
            if (dossier.getPieces() != null) {
                sb.append("- Pièces déjà jointes (").append(dossier.getPieces().size()).append(") : ")
                        .append(dossier.getPieces().stream()
                                .map(p -> piiAnonymizer.sanitizeText(p.getIntitule(), dossier))
                                .collect(Collectors.joining(", ")))
                        .append("\n");
            }
            if (caller != null && caller.getRole() != Role.ROLE_CITOYEN) {
                sb.append("- Mode staff actif : tu peux suggérer des vérifications de pièces, motifs de rejet ou transferts inter-services.\n");
            }
        }

        return sb.toString();
    }

    private AiSuggestion saveSuggestion(
            Dossier dossier,
            String typeAssistance,
            String fournisseur,
            String modele,
            String promptResume,
            String reponseJson,
            boolean modeDegrade,
            Utilisateur demandeur,
            Long latenceMs
    ) {
        try {
            AiSuggestion row = AiSuggestion.builder()
                    .dossier(dossier)
                    .typeAssistance(typeAssistance)
                    .fournisseur(fournisseur != null ? fournisseur : "FALLBACK_DETERMINISTE")
                    .modele(modele)
                    .resumePromptAnonymise(promptResume)
                    .reponseJson(reponseJson)
                    .suggestionIssuIa(true)
                    .modeDegrade(modeDegrade)
                    .demandeur(demandeur)
                    .latenceMs(latenceMs)
                    .build();
            return aiSuggestionRepository.save(row);
        } catch (Exception ex) {
            log.warn("[AI AUDIT] Impossible de journaliser ai_suggestions : {}", ex.getMessage());
            return AiSuggestion.builder().id(null).build();
        }
    }

    private AiSuggestionDto toSuggestionDto(AiSuggestion s) {
        return AiSuggestionDto.builder()
                .id(s.getId())
                .numeroSuivi(s.getDossier() != null ? s.getDossier().getNumeroSuivi() : null)
                .typeAssistance(s.getTypeAssistance())
                .fournisseur(s.getFournisseur())
                .modele(s.getModele())
                .suggestionIssuIa(s.isSuggestionIssuIa())
                .modeDegrade(s.isModeDegrade())
                .demandeurNom(s.getDemandeur() != null ? s.getDemandeur().getPrenom() + " " + s.getDemandeur().getNom() : "Public / Anonyme")
                .validateurNom(s.getValidateur() != null ? s.getValidateur().getPrenom() + " " + s.getValidateur().getNom() : null)
                .accepteeParAgent(s.getAccepteeParAgent())
                .latenceMs(s.getLatenceMs())
                .createdAt(s.getCreatedAt())
                .build();
    }

    private String toJsonSafe(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            return String.valueOf(obj);
        }
    }
}
