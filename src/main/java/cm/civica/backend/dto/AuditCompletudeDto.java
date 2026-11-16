package cm.civica.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditCompletudeDto {
    private String numeroSuivi;
    private String demarcheCode;
    private String demarcheTitre;
    private int scoreCompletude; // 0 - 100
    private boolean complet;

    @Builder.Default
    private List<String> piecesDetectees = new ArrayList<>();

    @Builder.Default
    private List<String> piecesManquantes = new ArrayList<>();

    @Builder.Default
    private List<String> recommandations = new ArrayList<>();

    private String syntheseAnalyse;
    private String avisIa; // "FAVORABLE", "DEFAVORABLE", "COMPLEMENT_REQUIS"

    // Traçabilité & transparence IA (Chapitre 8)
    @Builder.Default
    private String sourceProvider = "FALLBACK_DETERMINISTE";

    @Builder.Default
    private String modeleUtilise = "rule-based-v1";

    @Builder.Default
    private boolean modeDegrade = true;

    @Builder.Default
    private String niveauConfiance = "indicatif";

    private Long latenceMs;
    private Long suggestionId;
}
