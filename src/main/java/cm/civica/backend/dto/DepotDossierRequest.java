package cm.civica.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepotDossierRequest {

    @NotBlank(message = "Le code de la démarche est obligatoire")
    private String demarcheCode;

    private Long serviceId;

    private String priorite = "NORMALE";
}

