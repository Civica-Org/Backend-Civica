package cm.civica.backend.dto;

import cm.civica.backend.enums.StatutDossier;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DecisionDossierRequest {

    @NotNull(message = "Le nouveau statut est obligatoire")
    private StatutDossier statut; // EN_VERIFICATION, VALIDE, REJETE, RETIRE

    private String motifRejet;

    private String commentaire;
}

