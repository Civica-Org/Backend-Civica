package cm.civica.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateServiceRequest {
    @NotNull(message = "L'ID de l'organisation est obligatoire")
    private Long organisationId;

    @NotBlank(message = "Le code de service est obligatoire")
    private String code;

    @NotBlank(message = "Le nom du service administratif est obligatoire")
    private String nom;
}
