package cm.civica.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Requête de réassignation d'un dossier vers un agent spécifique du service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReassignationDossierRequest {

    @NotNull(message = "L'identifiant de l'agent cible est obligatoire")
    private Long agentId;

    private String noteResponsable;
}
