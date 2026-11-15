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
public class CreateOrganisationRequest {
    @NotBlank(message = "Le code organisation est obligatoire")
    private String code;

    @NotBlank(message = "Le nom de l'organisation est obligatoire")
    private String nom;

    @NotBlank(message = "Le type d'organisation est obligatoire")
    private String typeOrganisation;
}
