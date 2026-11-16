package cm.civica.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Représente la charge de travail actuelle d'un agent instructeur.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChargeDto {

    private Long agentId;
    private String nomComplet;
    private String email;
    private String telephone;
    private String role;
    private Long serviceId;
    private String serviceNom;
    private long dossiersEnCours;
    private long dossiersTraitesTotal;
    private boolean actif;
}

