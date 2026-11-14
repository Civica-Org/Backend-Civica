package cm.civica.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransfertDossierRequest {

    @NotNull(message = "L'identifiant du service cible est obligatoire")
    private Long serviceCibleId;

    private String commentaire;
}

