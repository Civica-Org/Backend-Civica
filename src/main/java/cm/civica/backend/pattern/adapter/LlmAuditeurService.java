package cm.civica.backend.pattern.adapter;

import cm.civica.backend.dto.AuditCompletudeDto;
import cm.civica.backend.model.Dossier;

/**
 * Interface Cible (Target) du Pattern Adapter — GoF (Structurel).
 *
 * Définit le contrat standard d'assistance IA pour l'audit de complétude
 * des dossiers et la suggestion de motifs de rejet.
 *
 * L'application CIVICA interagit uniquement avec cette interface, découplant
 * complètement le code métier des spécificités des fournisseurs d'IA
 * (OpenAI, Gemini, Ollama ou moteur heuristique local).
 */
public interface LlmAuditeurService {

    /**
     * Analyse un dossier et ses pièces jointes pour évaluer son taux de complétude
     * et identifier les éventuels documents manquants selon la réglementation camerounaise.
     */
    AuditCompletudeDto auditerDossier(Dossier dossier);

    /**
     * Suggère à l'agent un motif de rejet professionnel, clair et argumenté juridiquement.
     */
    String suggererMotifRejet(Dossier dossier, String contexte);
}

