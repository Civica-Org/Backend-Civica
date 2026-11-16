package cm.civica.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDemarcheRequest {
    @NotBlank(message = "Le code démarche est obligatoire")
    private String code;

    @NotBlank(message = "Le titre de la démarche est obligatoire")
    private String titre;

    private String description;

    @Min(value = 1, message = "Le délai indicatif doit être d'au moins 1 jour")
    private Integer delaiIndicatifJours;

    private Boolean actif;
}

