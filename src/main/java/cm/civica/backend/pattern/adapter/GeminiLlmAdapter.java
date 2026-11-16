package cm.civica.backend.pattern.adapter;

import cm.civica.backend.dto.AuditCompletudeDto;
import cm.civica.backend.dto.ia.CivicaIaChatResponse;
import cm.civica.backend.dto.ia.CivicaIaMessageDto;
import cm.civica.backend.dto.ia.ReformulateRejectionResponse;
import cm.civica.backend.model.Dossier;
import cm.civica.backend.model.PieceJustificative;
import cm.civica.backend.model.Utilisateur;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adaptateur Principal Gemini (Pattern Adapter — Chapitre 8).
 *
 * Interroge l'API gratuite Google Gemini avec anonymisation systématique préalable,
 * validation stricte du schéma JSON via Jackson et bascule transparente vers
 * {@link CivicaLlmAdapter} (RuleBasedCompletenessFallback) en cas de clé absente,
 * timeout (10 s) ou quota atteint.
 */
@Primary
@Service("geminiLlmAdapter")
public class GeminiLlmAdapter implements LlmAuditeurService {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmAdapter.class);

    private final GeminiRestClient geminiRestClient;
    private final CivicaLlmAdapter fallback;
    private final PiiAnonymizer piiAnonymizer;
    private final ObjectMapper objectMapper;

    public GeminiLlmAdapter(
            GeminiRestClient geminiRestClient,
            @Qualifier("civicaFallbackLlm") CivicaLlmAdapter fallback,
            PiiAnonymizer piiAnonymizer,
            ObjectMapper objectMapper
    ) {
        this.geminiRestClient = geminiRestClient;
        this.fallback = fallback;
        this.piiAnonymizer = piiAnonymizer;
        this.objectMapper = objectMapper;
    }

    @Override
    public AuditCompletudeDto auditerDossier(Dossier dossier) {
        String codeDemarche = dossier.getDemarche() != null ? dossier.getDemarche().getCode() : "INCONNUE";
        String titreDemarche = dossier.getDemarche() != null ? dossier.getDemarche().getTitre() : "Démarche administrative";

        List<String> piecesFournies = dossier.getPieces() != null
                ? dossier.getPieces().stream()
                .map(p -> piiAnonymizer.sanitizeText(describePiece(p), dossier))
                .collect(Collectors.toList())
                : Collections.emptyList();

        AuditCompletudeDto baseline = fallback.auditerDossier(dossier);
        if (!geminiRestClient.isConfigured()) {
            return baseline;
        }

        List<String> requiredDocs = fallback.getRequiredDocumentsFor(codeDemarche, null);
        AuditCompletudeDto aiResult = callGeminiAudit(
                codeDemarche,
                titreDemarche,
                null,
                requiredDocs,
                piecesFournies,
                dossier.getStatut() != null ? dossier.getStatut().name() : "SOUMIS"
        ).orElse(baseline);

        aiResult.setNumeroSuivi(dossier.getNumeroSuivi());
        return aiResult;
    }

    @Override
    public AuditCompletudeDto precheckPieces(
            String demarcheCode,
            String demarcheTitre,
            String varianteCircuit,
            List<String> intitulesPieces
    ) {
        List<String> sanitizedPieces = intitulesPieces != null
                ? intitulesPieces.stream().map(piiAnonymizer::sanitizeText).collect(Collectors.toList())
                : Collections.emptyList();

        AuditCompletudeDto baseline = fallback.precheckPieces(demarcheCode, demarcheTitre, varianteCircuit, sanitizedPieces);
        if (!geminiRestClient.isConfigured()) {
            return baseline;
        }

        List<String> requiredDocs = fallback.getRequiredDocumentsFor(demarcheCode, varianteCircuit);
        return callGeminiAudit(
                demarcheCode,
                demarcheTitre != null ? demarcheTitre : demarcheCode,
                varianteCircuit,
                requiredDocs,
                sanitizedPieces,
                "PRE_DEPOT"
        ).orElse(baseline);
    }

    private Optional<AuditCompletudeDto> callGeminiAudit(
            String codeDemarche,
            String titreDemarche,
            String varianteCircuit,
            List<String> requiredDocs,
            List<String> submittedDocs,
            String stageLabel
    ) {
        String systemPrompt = """
                Tu es Civica IA, auditeur consultatif de conformité documentaire pour la plateforme administrative camerounaise CIVICA.
                RÈGLE CARDINALE : Ton avis est strictement consultatif pour l'agent ou le citoyen. N'invente aucune donnée personnelle.
                Retourne UNIQUEMENT un objet JSON valide respectant exactement ce schéma :
                {
                  "complet": boolean,
                  "score_completude": integer (0..100),
                  "pieces_detectees": string[],
                  "pieces_manquantes": string[],
                  "recommandations": string[],
                  "synthese_analyse": string,
                  "avis_ia": "FAVORABLE" | "COMPLEMENT_REQUIS" | "DEFAVORABLE",
                  "niveau_confiance": "indicatif"
                }
                """;

        String userPrompt = String.format("""
                        Analyse la complétude documentaire suivante (données anonymisées) :
                        - Démarche : %s (%s)
                        - Variante circuit : %s
                        - Phase : %s
                        - Pièces officielles attendues : %s
                        - Pièces fournies par l'usager : %s
                        Réponds en français administratif clair, concis et bienveillant en JSON strict.
                        """,
                codeDemarche,
                titreDemarche,
                varianteCircuit != null && !varianteCircuit.isBlank() ? varianteCircuit : "STANDARD",
                stageLabel,
                requiredDocs,
                submittedDocs.isEmpty() ? List.of("(aucune pièce encore jointe)") : submittedDocs
        );

        Optional<GeminiRestClient.GeminiGenerationResult> responseOpt = geminiRestClient.generateJson(systemPrompt, userPrompt);
        if (responseOpt.isEmpty()) {
            return Optional.empty();
        }

        GeminiRestClient.GeminiGenerationResult gen = responseOpt.get();
        try {
            JsonNode json = objectMapper.readTree(gen.text());
            if (!json.has("complet") || !json.has("pieces_manquantes")) {
                log.warn("[GEMINI AUDIT] Réponse JSON incomplète, bascule sur repli déterministe.");
                return Optional.empty();
            }

            boolean complet = json.path("complet").asBoolean(false);
            int score = Math.max(0, Math.min(100, json.path("score_completude").asInt(complet ? 100 : 40)));
            List<String> detectees = readStringArray(json.path("pieces_detectees"));
            List<String> manquantes = readStringArray(json.path("pieces_manquantes"));
            List<String> recommandations = readStringArray(json.path("recommandations"));
            String synthese = json.path("synthese_analyse").asText(
                    String.format("Analyse Gemini (%s) : taux de complétude estimé à %d%%.", gen.modelUsed(), score)
            );
            String avisIa = validateAvis(json.path("avis_ia").asText("COMPLEMENT_REQUIS"));
            String confiance = json.path("niveau_confiance").asText("indicatif");

            return Optional.of(AuditCompletudeDto.builder()
                    .demarcheCode(codeDemarche)
                    .demarcheTitre(titreDemarche)
                    .scoreCompletude(score)
                    .complet(complet)
                    .piecesDetectees(detectees)
                    .piecesManquantes(manquantes)
                    .recommandations(recommandations)
                    .syntheseAnalyse(synthese)
                    .avisIa(avisIa)
                    .sourceProvider("GEMINI_REST")
                    .modeleUtilise(gen.modelUsed())
                    .modeDegrade(false)
                    .niveauConfiance(confiance)
                    .latenceMs(gen.latencyMs())
                    .build());
        } catch (Exception ex) {
            log.warn("[GEMINI AUDIT] Échec de validation du JSON retourné ({}), repli déterministe activé : {}",
                    gen.modelUsed(), ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public String suggererMotifRejet(Dossier dossier, String contexte) {
        return reformulerMotifCourtois(dossier, null, contexte).getSuggestionCourtoise();
    }

    @Override
    public ReformulateRejectionResponse reformulerMotifCourtois(Dossier dossier, String motifTechnique, String contexte) {
        ReformulateRejectionResponse baseline = fallback.reformulerMotifCourtois(dossier, motifTechnique, contexte);
        if (!geminiRestClient.isConfigured()) {
            return baseline;
        }

        String sanitizedMotif = piiAnonymizer.sanitizeText(motifTechnique, dossier);
        String sanitizedContexte = piiAnonymizer.sanitizeText(contexte, dossier);
        String codeDemarche = dossier.getDemarche() != null ? dossier.getDemarche().getCode() : "DEMARCHE";
        String titreDemarche = dossier.getDemarche() != null ? dossier.getDemarche().getTitre() : "Démarche administrative";
        List<String> expectedDocs = fallback.getRequiredDocumentsFor(codeDemarche, null);
        List<String> providedDocs = dossier.getPieces() != null
                ? dossier.getPieces().stream().map(p -> piiAnonymizer.sanitizeText(p.getIntitule(), dossier)).toList()
                : List.of();

        String systemPrompt = """
                Tu es Civica IA, assistant rédactionnel pour les agents de l'administration camerounaise (Chapitre 8).
                Reformule le motif de rejet administratif en un message clair, courtois, pédagogique et précis pour le citoyen.
                Indique comment compléter ou régulariser la demande via l'espace Web ou Mobile CIVICA.
                N'inclus aucune donnée personnelle fictive non fournie. Réponds uniquement par le texte brut final (sans balises Markdown ni astérisques **) prêt à être relu par l'agent.
                """;

        String userPrompt = String.format("""
                        Démarche : %s (%s)
                        Pièces attendues : %s
                        Pièces fournies (anonymisées) : %s
                        Motif technique saisi par l'agent : %s
                        Contexte additionnel agent : %s
                        """,
                titreDemarche,
                codeDemarche,
                expectedDocs,
                providedDocs,
                sanitizedMotif.isBlank() ? "(aucun motif saisi, rédiger d'après les pièces manquantes)" : sanitizedMotif,
                sanitizedContexte.isBlank() ? "(aucun)" : sanitizedContexte
        );

        Optional<GeminiRestClient.GeminiGenerationResult> genOpt =
                geminiRestClient.generateChat(systemPrompt, List.of(), userPrompt);

        if (genOpt.isEmpty()) {
            return baseline;
        }

        GeminiRestClient.GeminiGenerationResult gen = genOpt.get();
        return ReformulateRejectionResponse.builder()
                .numeroSuivi(dossier.getNumeroSuivi())
                .motifOriginal(baseline.getMotifOriginal())
                .suggestionCourtoise(gen.text())
                .sourceProvider("GEMINI_REST")
                .modeleUtilise(gen.modelUsed())
                .modeDegrade(false)
                .latenceMs(gen.latencyMs())
                .build();
    }

    @Override
    public CivicaIaChatResponse repondreAssistant(
            String sanitizedQuestion,
            String roleContext,
            String groundingContext,
            String pageContexte,
            List<CivicaIaMessageDto> history,
            Dossier optionalDossier,
            Utilisateur callerOrNull
    ) {
        if (!geminiRestClient.isConfigured()) {
            return fallback.repondreAssistant(
                    sanitizedQuestion, roleContext, groundingContext, pageContexte, history, optionalDossier, callerOrNull
            );
        }

        String systemPrompt = String.format("""
                Tu es « Civica IA », l'assistant officiel de la plateforme CIVICA (dématérialisation et suivi des démarches administratives au Cameroun : CNI biométrique DGSN, Actes d'état civil Mairies, Bulletin N°3 Casier Judiciaire TPI).
                
                Profil de l'interlocuteur : %s
                Page active : %s
                
                Référentiel réglementaire et contexte plateforme anonymisé :
                %s
                
                Règles de réponse :
                1. Réponds toujours en français clair, structuré (sous-titres `##` si utile, puces `-` pour les listes, termes clés et intitulés de pièces en gras `**...**`, montants en FCFA) et directement actionnable.
                2. Rappel permanent : place une courte note consultative (ex. `> Note importante : Mon avis est purement consultatif ; toute décision administrative officielle relève exclusivement d'un agent ou officier habilité.`).
                3. Ne mentionne jamais de clés secrètes et n'affirme pas avoir accédé à des documents non listés dans le contexte.
                4. Adapte tes conseils au profil (ex. pour un citoyen : pièces, tarifs FCFA, suivi ; pour un agent/responsable : audit de complétude, transfert inter-services avec unicité de détention, reformulation rejet, double validation des retraits financiers).
                """,
                roleContext,
                pageContexte != null && !pageContexte.isBlank() ? pageContexte : "Portail CIVICA",
                groundingContext
        );

        List<CivicaIaMessageDto> sanitizedHistory = new ArrayList<>();
        if (history != null) {
            for (CivicaIaMessageDto m : history) {
                if (m != null && m.getContent() != null) {
                    sanitizedHistory.add(new CivicaIaMessageDto(
                            m.getRole(),
                            piiAnonymizer.sanitizeText(m.getContent(), optionalDossier)
                    ));
                }
            }
        }

        Optional<GeminiRestClient.GeminiGenerationResult> genOpt =
                geminiRestClient.generateChat(systemPrompt, sanitizedHistory, sanitizedQuestion);

        if (genOpt.isEmpty()) {
            return fallback.repondreAssistant(
                    sanitizedQuestion, roleContext, groundingContext, pageContexte, history, optionalDossier, callerOrNull
            );
        }

        GeminiRestClient.GeminiGenerationResult gen = genOpt.get();
        List<String> quickReplies = buildContextualQuickReplies(roleContext, pageContexte);

        return CivicaIaChatResponse.builder()
                .reponse(gen.text())
                .sourceProvider("GEMINI_REST")
                .modeleUtilise(gen.modelUsed())
                .modeDegrade(false)
                .latenceMs(gen.latencyMs())
                .avertissement("Suggestion consultative générée par Civica IA (Gemini · données personnelles préalablement anonymisées).")
                .suggestionsRapides(quickReplies)
                .build();
    }

    private List<String> buildContextualQuickReplies(String roleContext, String pageContexte) {
        String r = roleContext != null ? roleContext.toUpperCase() : "";
        if (r.contains("AGENT") || r.contains("SOUS_RESPONSABLE")) {
            return List.of(
                    "Comment qualifier un rejet de CNI ?",
                    "Règles de transfert inter-services ?",
                    "Vérifier une déclaration tardive d'acte de naissance"
            );
        }
        if (r.contains("RESPONSABLE") || r.contains("ADMIN")) {
            return List.of(
                    "Expliquer la double validation des retraits",
                    "Comment sont ventilés les paiements en FCFA ?",
                    "Audit des suggestions IA en base"
            );
        }
        if (pageContexte != null && pageContexte.toLowerCase().contains("deposer")) {
            return List.of(
                    "Quelles pièces pour mon dossier ?",
                    "Combien coûte cette démarche ?",
                    "Comment scanner un document lisible ?"
            );
        }
        return List.of(
                "Pièces pour la CNI biométrique ?",
                "Circuits et tarifs de l'acte de naissance ?",
                "Comment payer et obtenir ma quittance ?"
        );
    }

    private String describePiece(PieceJustificative piece) {
        return piece.getIntitule() + " (" + piece.getTypeMime() + ", " + (piece.getTailleOctets() / 1024) + " Ko)";
    }

    private List<String> readStringArray(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (!item.asText("").isBlank()) {
                    result.add(item.asText().trim());
                }
            }
        }
        return result;
    }

    private String validateAvis(String raw) {
        if ("FAVORABLE".equalsIgnoreCase(raw)) return "FAVORABLE";
        if ("DEFAVORABLE".equalsIgnoreCase(raw)) return "DEFAVORABLE";
        return "COMPLEMENT_REQUIS";
    }
}

