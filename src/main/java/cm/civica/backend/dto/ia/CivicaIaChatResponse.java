package cm.civica.backend.dto.ia;

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
public class CivicaIaChatResponse {
    private String reponse;
    private String sourceProvider;
    private String modeleUtilise;
    private boolean modeDegrade;
    private long latenceMs;
    private String avertissement;

    @Builder.Default
    private List<String> suggestionsRapides = new ArrayList<>();
}

