package cm.civica.backend.dto.ia;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReformulateRejectionResponse {
    private String numeroSuivi;
    private String motifOriginal;
    private String suggestionCourtoise;
    private String sourceProvider;
    private String modeleUtilise;
    private boolean modeDegrade;
    private long latenceMs;
    private Long suggestionId;
}

