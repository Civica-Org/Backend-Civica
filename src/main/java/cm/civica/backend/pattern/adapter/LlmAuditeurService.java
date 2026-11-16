package cm.civica.backend.pattern.adapter;

import cm.civica.backend.dto.AuditCompletudeDto;
import cm.civica.backend.dto.ia.CivicaIaChatResponse;
import cm.civica.backend.dto.ia.CivicaIaMessageDto;
import cm.civica.backend.dto.ia.ReformulateRejectionResponse;
import cm.civica.backend.model.Dossier;
import cm.civica.backend.model.Utilisateur;

import java.util.List;

/**
 * Interface Cible (Target) du Pattern Adapter — GoF (Structurel).
 *
 * Définit le contrat standard d'assistance IA de CIVICA :
 * - Audit de complétude documentaire (agent et pré-contrôle citoyen avant dépôt)
 * - Suggestion et reformulation pédagogique des motifs de rejet (Chapitre 8 §B)
 * - Assistant conversationnel contextuel multi-interfaces (Web & Mobile)
 */
public interface LlmAuditeurService {

    /**
     * Analyse un dossier et ses pièces jointes pour évaluer son taux de complétude
     * et identifier les éventuels documents manquants selon la réglementation camerounaise.
     */
    AuditCompletudeDto auditerDossier(Dossier dossier);

    /**
     * Pré-contrôle de complétude avant dépôt (Web citoyen / Application Mobile).
     */
    AuditCompletudeDto precheckPieces(
            String demarcheCode,
            String demarcheTitre,
            String varianteCircuit,
            List<String> intitulesPieces
    );

    /**
     * Suggère à l'agent un motif de rejet professionnel, clair et argumenté juridiquement.
     */
    String suggererMotifRejet(Dossier dossier, String contexte);

    /**
     * Reformule de façon courtoise et pédagogique un motif de rejet technique rédigé par un agent (Chapitre 8 §B).
     */
    ReformulateRejectionResponse reformulerMotifCourtois(Dossier dossier, String motifTechnique, String contexte);

    /**
     * Répond à une question conversationnelle dans l'assistant Civica IA (Web & Mobile),
     * avec prise en compte sécurisée du contexte métier (anonyme ou rôle connecté).
     */
    CivicaIaChatResponse repondreAssistant(
            String sanitizedQuestion,
            String roleContext,
            String groundingContext,
            String pageContexte,
            List<CivicaIaMessageDto> history,
            Dossier optionalDossier,
            Utilisateur callerOrNull
    );
}
