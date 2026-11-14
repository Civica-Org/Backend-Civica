package cm.civica.backend.pattern.adapter;

import cm.civica.backend.dto.AuditCompletudeDto;
import cm.civica.backend.model.Dossier;
import cm.civica.backend.model.PieceJustificative;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Adaptateur concret du Pattern Adapter — GoF.
 *
 * Implémente l'interface cible {@link LlmAuditeurService}.
 * Analyse la complétude documentaire en se basant sur le référentiel officiel
 * des démarches administratives au Cameroun (DGSN, Mairies, Tribunaux).
 */
@Service
public class CivicaLlmAdapter implements LlmAuditeurService {

    private static final Logger log = LoggerFactory.getLogger(CivicaLlmAdapter.class);

    // Référentiel des pièces requises par démarche
    private static final Map<String, List<String>> PIECES_REQUISES_PAR_DEMARCHE = new HashMap<>();

    static {
        // CNI biométrique (DGSN)
        PIECES_REQUISES_PAR_DEMARCHE.put("DEM-CNI", Arrays.asList(
                "Copie certifiée conforme d'acte de naissance",
                "Certificat de nationalité camerounaise",
                "Justificatif de profession ou diplôme"
        ));

        // Acte de naissance (Mairies)
        PIECES_REQUISES_PAR_DEMARCHE.put("DEM-ACTE-NAISS", Arrays.asList(
                "Déclaration de naissance délivrée par la maternité",
                "Photocopies des CNI des deux parents"
        ));

        // Casier judiciaire (TPI)
        PIECES_REQUISES_PAR_DEMARCHE.put("DEM-CASIER-JUD", Arrays.asList(
                "Photocopie lisible de la Carte Nationale d'Identité",
                "Copie d'acte de naissance légalisée"
        ));
    }

    @Override
    public AuditCompletudeDto auditerDossier(Dossier dossier) {
        log.info("[LLM ADAPTER] Audit de complétude pour le dossier {}", dossier.getNumeroSuivi());

        String codeDemarche = dossier.getDemarche() != null ? dossier.getDemarche().getCode() : "";
        String titreDemarche = dossier.getDemarche() != null ? dossier.getDemarche().getTitre() : "Démarche standard";

        List<String> piecesAttendues = PIECES_REQUISES_PAR_DEMARCHE.getOrDefault(codeDemarche,
                Collections.singletonList("Document justificatif d'identité"));

        List<PieceJustificative> piecesFournies = dossier.getPieces() != null ? dossier.getPieces() : Collections.emptyList();

        List<String> intitulesFournis = piecesFournies.stream()
                .map(PieceJustificative::getIntitule)
                .collect(Collectors.toList());

        List<String> piecesDetectees = new ArrayList<>();
        List<String> piecesManquantes = new ArrayList<>();

        for (String attendue : piecesAttendues) {
            boolean detectee = intitulesFournis.stream()
                    .anyMatch(fourni -> matchIntitule(fourni, attendue));
            if (detectee) {
                piecesDetectees.add(attendue);
            } else {
                piecesManquantes.add(attendue);
            }
        }

        int score = piecesAttendues.isEmpty() ? 100 : (int) Math.round(((double) piecesDetectees.size() / piecesAttendues.size()) * 100);
        boolean complet = piecesManquantes.isEmpty();

        List<String> recommandations = new ArrayList<>();
        String avisIa;
        if (complet) {
            avisIa = "FAVORABLE";
            recommandations.add("Toutes les pièces requises semblent réunies.");
            recommandations.add("Vérifier la lisibilité des scans et la validité des tampons légaux.");
        } else if (score >= 50) {
            avisIa = "COMPLEMENT_REQUIS";
            recommandations.add("Demander au citoyen de téléverser les pièces manquantes avant instruction finale.");
            recommandations.add("Document(s) à réclamer en priorité : " + String.join(", ", piecesManquantes));
        } else {
            avisIa = "DEFAVORABLE";
            recommandations.add("Dossier hautement incomplet. Rejet préliminaire ou demande formelle de complément.");
        }

        String synthese = String.format(
                "Analyse assistée par IA : le dossier présente un taux de complétude estimé à %d%% (%d/%d pièce(s) requise(s)). Statut recommandé : %s.",
                score, piecesDetectees.size(), piecesAttendues.size(), avisIa
        );

        return AuditCompletudeDto.builder()
                .numeroSuivi(dossier.getNumeroSuivi())
                .demarcheCode(codeDemarche)
                .demarcheTitre(titreDemarche)
                .scoreCompletude(score)
                .complet(complet)
                .piecesDetectees(piecesDetectees)
                .piecesManquantes(piecesManquantes)
                .recommandations(recommandations)
                .syntheseAnalyse(synthese)
                .avisIa(avisIa)
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
                .append("', nous vous informons que votre demande ne peut être instruite favorablement en l'état.\n\n");

        if (!audit.getPiecesManquantes().isEmpty()) {
            sb.append("Motif principal : Pièce(s) justificative(s) obligatoire(s) manquante(s) ou non conforme(s) :\n");
            for (String manquante : audit.getPiecesManquantes()) {
                sb.append(" - ").append(manquante).append("\n");
            }
        }

        if (contexte != null && !contexte.isBlank()) {
            sb.append("\nObservations complémentaires de l'instructeur :\n").append(contexte).append("\n");
        }

        sb.append("\nVous êtes invité(e) à constituer les pièces requises et à effectuer une nouvelle soumission via la plateforme CIVICA.");
        return sb.toString();
    }

    private boolean matchIntitule(String fourni, String attendue) {
        String f = fourni.toLowerCase(Locale.ROOT);
        String a = attendue.toLowerCase(Locale.ROOT);
        if (a.contains("naissance") && f.contains("naissance")) return true;
        if (a.contains("nationalité") && (f.contains("nationalite") || f.contains("nationalité"))) return true;
        if (a.contains("profession") && (f.contains("profession") || f.contains("travail") || f.contains("diplome") || f.contains("diplôme"))) return true;
        if (a.contains("cni") && f.contains("cni")) return true;
        if (a.contains("mariage") && f.contains("mariage")) return true;
        return f.contains(a) || a.contains(f);
    }
}

