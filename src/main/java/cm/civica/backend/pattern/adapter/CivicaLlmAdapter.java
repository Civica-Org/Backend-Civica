package cm.civica.backend.pattern.adapter;

import cm.civica.backend.dto.AuditCompletudeDto;
import cm.civica.backend.dto.ia.CivicaIaChatResponse;
import cm.civica.backend.dto.ia.CivicaIaMessageDto;
import cm.civica.backend.dto.ia.ReformulateRejectionResponse;
import cm.civica.backend.model.Dossier;
import cm.civica.backend.model.PieceJustificative;
import cm.civica.backend.model.Utilisateur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Adaptateur Déterministe / Repli (RuleBasedCompletenessFallback — Chapitre 8).
 *
 * Implémente {@link LlmAuditeurService} par règles métier déterministes locales sur le
 * référentiel officiel camerounais (DGSN, Mairies, Tribunaux). Utilisé automatiquement
 * lorsque l'API Gemini externe est indisponible, limitée en quota ou non configurée.
 */
@Service("civicaFallbackLlm")
public class CivicaLlmAdapter implements LlmAuditeurService {

    private static final Logger log = LoggerFactory.getLogger(CivicaLlmAdapter.class);
    private static final Map<String, List<String>> PIECES_REQUISES_PAR_DEMARCHE = new HashMap<>();

    static {
        // CNI biométrique (DGSN)
        PIECES_REQUISES_PAR_DEMARCHE.put("DEM-CNI", Arrays.asList(
                "Copie certifiée conforme d'acte de naissance",
                "Certificat de nationalité camerounaise",
                "Justificatif de profession ou diplôme"
        ));

        // Acte de naissance - circuit standard / extrait / déclaration normale
        PIECES_REQUISES_PAR_DEMARCHE.put("DEM-ACTE-NAISS", Arrays.asList(
                "Déclaration de naissance délivrée par la maternité",
                "Photocopies des CNI des deux parents"
        ));

        // Acte de naissance - variantes spécifiques
        PIECES_REQUISES_PAR_DEMARCHE.put("DEM-ACTE-NAISS:EXTRAIT_COPIE", Arrays.asList(
                "Ancien acte ou référence de souche d'état civil",
                "Photocopie de la CNI du demandeur ou d'un parent"
        ));
        PIECES_REQUISES_PAR_DEMARCHE.put("DEM-ACTE-NAISS:TARDIVE", Arrays.asList(
                "Certificat d'accouchement ou attestation médicale tardive",
                "Photocopies des CNI des deux parents",
                "Demande motivée adressée au Procureur de la République"
        ));
        PIECES_REQUISES_PAR_DEMARCHE.put("DEM-ACTE-NAISS:JUDICIAIRE", Arrays.asList(
                "Requête en jugement supplétif d'acte de naissance",
                "Certificat de non-inscription au registre communal",
                "Pièces d'identité des témoins / parents"
        ));

        // Casier judiciaire (TPI)
        PIECES_REQUISES_PAR_DEMARCHE.put("DEM-CASIER-JUD", Arrays.asList(
                "Photocopie lisible de la Carte Nationale d'Identité",
                "Copie d'acte de naissance légalisée"
        ));
    }

    public List<String> getRequiredDocumentsFor(String demarcheCode, String varianteCircuit) {
        String code = demarcheCode != null ? demarcheCode.trim().toUpperCase(Locale.ROOT) : "";
        if (varianteCircuit != null && !varianteCircuit.isBlank()) {
            String keyWithVar = code + ":" + varianteCircuit.trim().toUpperCase(Locale.ROOT);
            if (PIECES_REQUISES_PAR_DEMARCHE.containsKey(keyWithVar)) {
                return PIECES_REQUISES_PAR_DEMARCHE.get(keyWithVar);
            }
        }
        return PIECES_REQUISES_PAR_DEMARCHE.getOrDefault(
                code,
                Collections.singletonList("Document justificatif d'identité")
        );
    }

    @Override
    public AuditCompletudeDto auditerDossier(Dossier dossier) {
        long start = System.currentTimeMillis();
        log.info("[LLM FALLBACK] Audit déterministe pour le dossier {}", dossier.getNumeroSuivi());

        String codeDemarche = dossier.getDemarche() != null ? dossier.getDemarche().getCode() : "";
        String titreDemarche = dossier.getDemarche() != null ? dossier.getDemarche().getTitre() : "Démarche standard";

        List<String> piecesFournies = dossier.getPieces() != null
                ? dossier.getPieces().stream().map(PieceJustificative::getIntitule).collect(Collectors.toList())
                : Collections.emptyList();

        AuditCompletudeDto dto = evaluateCompleteness(codeDemarche, titreDemarche, null, piecesFournies);
        dto.setNumeroSuivi(dossier.getNumeroSuivi());
        dto.setLatenceMs(System.currentTimeMillis() - start);
        return dto;
    }

    @Override
    public AuditCompletudeDto precheckPieces(
            String demarcheCode,
            String demarcheTitre,
            String varianteCircuit,
            List<String> intitulesPieces
    ) {
        long start = System.currentTimeMillis();
        AuditCompletudeDto dto = evaluateCompleteness(
                demarcheCode,
                demarcheTitre != null ? demarcheTitre : demarcheCode,
                varianteCircuit,
                intitulesPieces != null ? intitulesPieces : Collections.emptyList()
        );
        dto.setLatenceMs(System.currentTimeMillis() - start);
        return dto;
    }

    private AuditCompletudeDto evaluateCompleteness(
            String codeDemarche,
            String titreDemarche,
            String varianteCircuit,
            List<String> intitulesFournis
    ) {
        List<String> piecesAttendues = getRequiredDocumentsFor(codeDemarche, varianteCircuit);
        List<String> piecesDetectees = new ArrayList<>();
        List<String> piecesManquantes = new ArrayList<>();

        for (String attendue : piecesAttendues) {
            boolean detectee = intitulesFournis.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(fourni -> matchIntitule(fourni, attendue));
            if (detectee) {
                piecesDetectees.add(attendue);
            } else {
                piecesManquantes.add(attendue);
            }
        }

        int score = piecesAttendues.isEmpty()
                ? 100
                : (int) Math.round(((double) piecesDetectees.size() / piecesAttendues.size()) * 100);
        boolean complet = piecesManquantes.isEmpty();

        List<String> recommandations = new ArrayList<>();
        String avisIa;
        if (complet) {
            avisIa = "FAVORABLE";
            recommandations.add("Toutes les pièces requises par le référentiel déterministe semblent réunies.");
            recommandations.add("Vérifier la lisibilité des scans et la validité des timbres ou cachets officiels.");
        } else if (score >= 50) {
            avisIa = "COMPLEMENT_REQUIS";
            recommandations.add("Demander au citoyen de joindre les pièces manquantes identifiées avant décision finale.");
            recommandations.add("Document(s) à compléter : " + String.join(", ", piecesManquantes));
        } else {
            avisIa = "DEFAVORABLE";
            recommandations.add("Dossier très incomplet par rapport au référentiel réglementaire.");
        }

        String synthese = String.format(
                "Pré-contrôle déterministe : taux de complétude estimé à %d%% (%d/%d pièce(s) requise(s)). Avis indicatif : %s.",
                score, piecesDetectees.size(), piecesAttendues.size(), avisIa
        );

        return AuditCompletudeDto.builder()
                .demarcheCode(codeDemarche)
                .demarcheTitre(titreDemarche)
                .scoreCompletude(score)
                .complet(complet)
                .piecesDetectees(piecesDetectees)
                .piecesManquantes(piecesManquantes)
                .recommandations(recommandations)
                .syntheseAnalyse(synthese)
                .avisIa(avisIa)
                .sourceProvider("FALLBACK_DETERMINISTE")
                .modeleUtilise("rule-based-v1")
                .modeDegrade(true)
                .niveauConfiance("indicatif (règles déterministes)")
                .build();
    }

    @Override
    public String suggererMotifRejet(Dossier dossier, String contexte) {
        AuditCompletudeDto audit = auditerDossier(dossier);
        StringBuilder sb = new StringBuilder();
        sb.append("Notification de rejet administratif - Dossier N° ").append(dossier.getNumeroSuivi()).append("\n\n");
        sb.append("Madame, Monsieur,\n\n");
        sb.append("Après examen de votre dossier relatif à la démarche '")
                .append(dossier.getDemarche() != null ? dossier.getDemarche().getTitre() : "demandée")
                .append("', votre demande ne peut poursuivre son traitement en l'état.\n\n");

        if (!audit.getPiecesManquantes().isEmpty()) {
            sb.append("Pièce(s) justificative(s) obligatoire(s) manquante(s) ou à régulariser :\n");
            for (String manquante : audit.getPiecesManquantes()) {
                sb.append(" - ").append(manquante).append("\n");
            }
        }

        if (contexte != null && !contexte.isBlank()) {
            sb.append("\nPrécision de l'instructeur : ").append(contexte.trim()).append("\n");
        }

        sb.append("\nVeuillez téléverser les éléments demandés depuis votre espace Web ou Mobile CIVICA afin de reprendre l'instruction.");
        return sb.toString();
    }

    @Override
    public ReformulateRejectionResponse reformulerMotifCourtois(Dossier dossier, String motifTechnique, String contexte) {
        long start = System.currentTimeMillis();
        String base = (motifTechnique != null && !motifTechnique.isBlank())
                ? motifTechnique.trim()
                : suggererMotifRejet(dossier, contexte);

        String titre = dossier.getDemarche() != null ? dossier.getDemarche().getTitre() : "votre démarche";
        String reformule = String.format(
                "Bonjour,\n\nVotre dossier relatif à « %s » nécessite une action complémentaire avant de pouvoir poursuivre son instruction : %s.\n\n"
                        + "Nous vous invitons à ajouter ou corriger cet élément directement depuis votre espace Web ou Mobile CIVICA afin de permettre la reprise rapide de votre demande.",
                titre,
                base.replaceFirst("\\.$", "")
        );

        return ReformulateRejectionResponse.builder()
                .numeroSuivi(dossier.getNumeroSuivi())
                .motifOriginal(base)
                .suggestionCourtoise(reformule)
                .sourceProvider("FALLBACK_DETERMINISTE")
                .modeleUtilise("rule-based-v1")
                .modeDegrade(true)
                .latenceMs(System.currentTimeMillis() - start)
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
        long start = System.currentTimeMillis();
        String q = (sanitizedQuestion != null ? sanitizedQuestion : "").toLowerCase(Locale.ROOT);
        String answer;
        List<String> quickReplies;

        if (q.contains("cni") || q.contains("identité") || q.contains("identite")) {
            answer = "Pour la Carte Nationale d'Identité biométrique (DEM-CNI · DGSN) :\n"
                    + "• Pièces requises : copie certifiée d'acte de naissance, certificat de nationalité camerounaise, justificatif de profession ou diplôme.\n"
                    + "• Tarif officiel CIVICA : 10 000 FCFA (répartis à 70 % pour la DGSN et 30 % pour la Commune d'enrôlement).\n"
                    + "• Délai indicatif : 14 jours.";
            quickReplies = List.of("Quels sont les frais du casier judiciaire ?", "Comment payer en ligne ?", "Où suivre mon dossier ?");
        } else if (q.contains("naissance") || q.contains("état civil") || q.contains("etat civil") || q.contains("supplétif") || q.contains("tardiv")) {
            answer = "Pour l'Acte de Naissance (DEM-ACTE-NAISS) sur CIVICA, le circuit dépend du délai :\n"
                    + "• Déclaration normale (≤ 90 jours) : gratuite (0 FCFA), instruite en Mairie.\n"
                    + "• Extrait / copie certifiée : 1 000 FCFA (100 % Commune).\n"
                    + "• Déclaration tardive (91 à 365 jours · réquisition Parquet) : 1 500 FCFA (60 % Commune / 40 % TPI).\n"
                    + "• Jugement supplétif (> 365 jours · TPI) : 5 000 FCFA (70 % TPI / 30 % Commune).";
            quickReplies = List.of("Pièces pour déclaration tardive ?", "Combien coûte la CNI ?", "Délai moyen acte de naissance ?");
        } else if (q.contains("casier") || q.contains("bulletin") || q.contains("tpi") || q.contains("tribunal")) {
            answer = "Pour le Bulletin N°3 du Casier Judiciaire (DEM-CASIER-JUD · Tribunal de Première Instance) :\n"
                    + "• Pièces requises : photocopie lisible de la CNI et copie d'acte de naissance légalisée.\n"
                    + "• Tarif officiel CIVICA : 2 500 FCFA (80 % TPI, 20 % DGSN).\n"
                    + "• Délai indicatif : 2 jours ouvrables.";
            quickReplies = List.of("Comment télécharger ma quittance ?", "Pièces pour la CNI ?", "Statut EN_VERIFICATION ?");
        } else if (q.contains("paie") || q.contains("frais") || q.contains("tarif") || q.contains("quittance") || q.contains("momo") || q.contains("orange")) {
            answer = "Les règlements CIVICA s'effectuent en FCFA (XAF) depuis la page de suivi ou juste après le dépôt via MTN Mobile Money, Orange Money, Carte Bancaire ou Guichet Trésor.\n"
                    + "Chaque paiement confirmé génère une quittance officielle (QUIT-CM-*) et crédite instantanément la quote-part de chaque institution concernée.";
            quickReplies = List.of("Comment fonctionne la double validation des retraits ?", "Où trouver ma quittance ?");
        } else if (q.contains("4 yeux") || q.contains("quatre yeux") || q.contains("double validation") || q.contains("retrait") || q.contains("trésor") || q.contains("tresor")) {
            answer = "La double validation financière (Livrable 15 / BPMN P7) impose une stricte séparation entre l'ordonnateur et le contrôleur financier :\n"
                    + "1. Un premier responsable initie le retrait (statut DEMANDE, fonds déplacés en réserve bloquée).\n"
                    + "2. Un second responsable distinct doit contre-signer (EXECUTE) ou rejeter (REFUSE avec déblocage immédiat). L'auto-approbation est bloquée côté backend et en base de données.";
            quickReplies = List.of("Comment transférer un dossier ?", "Règles de décision d'un agent ?");
        } else if (optionalDossier != null) {
            answer = String.format(
                    "Votre dossier %s (%s) est actuellement au statut « %s » au sein du service : %s.%s",
                    optionalDossier.getNumeroSuivi(),
                    optionalDossier.getDemarche() != null ? optionalDossier.getDemarche().getTitre() : "Démarche",
                    optionalDossier.getStatut(),
                    optionalDossier.getServiceCourant() != null ? optionalDossier.getServiceCourant().getNom() : "En attente d'affectation",
                    optionalDossier.getMotifRejet() != null && !optionalDossier.getMotifRejet().isBlank()
                            ? "\nMotif enregistré : " + optionalDossier.getMotifRejet()
                            : ""
            );
            quickReplies = List.of("Quelles pièces manquent ?", "Frais de cette démarche ?");
        } else {
            answer = "Bonjour ! Je suis Civica IA (mode assistance locale hors-ligne).\n"
                    + "Je peux vous renseigner sur les pièces justificatives (CNI, Acte de naissance, Casier judiciaire), les tarifs légaux en FCFA, le suivi de votre dossier, les paiements/quittances ou les procédures agents (transferts, audits de complétude, double validation financière).";
            quickReplies = List.of(
                    "Pièces requises pour la CNI ?",
                    "Tarifs de l'acte de naissance ?",
                    "Frais du casier judiciaire ?",
                    "Comment suivre ou payer un dossier ?"
            );
        }

        return CivicaIaChatResponse.builder()
                .reponse(answer)
                .sourceProvider("FALLBACK_DETERMINISTE")
                .modeleUtilise("rule-based-v1")
                .modeDegrade(true)
                .latenceMs(System.currentTimeMillis() - start)
                .avertissement("Réponse indicative générée localement (mode repli déterministe). La décision administrative reste humaine.")
                .suggestionsRapides(quickReplies)
                .build();
    }

    private boolean matchIntitule(String fourni, String attendue) {
        String f = fourni.toLowerCase(Locale.ROOT);
        String a = attendue.toLowerCase(Locale.ROOT);
        if (a.contains("naissance") && f.contains("naissance")) return true;
        if (a.contains("nationalité") && (f.contains("nationalite") || f.contains("nationalité"))) return true;
        if (a.contains("profession") && (f.contains("profession") || f.contains("travail") || f.contains("diplome") || f.contains("diplôme"))) return true;
        if (a.contains("cni") && (f.contains("cni") || f.contains("identité") || f.contains("identite"))) return true;
        if (a.contains("maternité") && (f.contains("maternit") || f.contains("accouchement") || f.contains("naissance"))) return true;
        if (a.contains("procureur") && (f.contains("procureur") || f.contains("parquet") || f.contains("requête") || f.contains("requete"))) return true;
        if (a.contains("jugement") && (f.contains("jugement") || f.contains("requête") || f.contains("requete"))) return true;
        if (a.contains("mariage") && f.contains("mariage")) return true;
        return f.contains(a) || a.contains(f);
    }
}
