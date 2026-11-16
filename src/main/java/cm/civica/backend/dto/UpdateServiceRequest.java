package cm.civica.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateServiceRequest {
    private Long organisationId;

    @NotBlank(message = "Le code de service est obligatoire")
    private String code;

    @NotBlank(message = "Le nom du service administratif est obligatoire")
    private String nom;

    private Boolean actif;
}

