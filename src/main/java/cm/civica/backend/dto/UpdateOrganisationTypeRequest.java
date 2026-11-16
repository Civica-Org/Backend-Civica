package cm.civica.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrganisationTypeRequest {

    @NotBlank(message = "Le libellé du type d'organisation est obligatoire")
    @Size(max = 150, message = "Le libellé ne peut pas dépasser 150 caractères")
    private String libelle;

    @Size(max = 300, message = "La description ne peut pas dépasser 300 caractères")
    private String description;

    private Boolean actif;
}

