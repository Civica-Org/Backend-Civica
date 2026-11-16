package cm.civica.backend.dto.ia;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSuggestionDto {
    private Long id;
    private String numeroSuivi;
    private String typeAssistance;
    private String fournisseur;
    private String modele;
    private boolean suggestionIssuIa;
    private boolean modeDegrade;
    private String demandeurNom;
    private String validateurNom;
    private Boolean accepteeParAgent;
    private Long latenceMs;
    private Instant createdAt;
}

