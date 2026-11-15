package cm.civica.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardStatsDto {
    private long totalOrganisations;
    private long totalServices;
    private long totalDemarches;
    private long totalUtilisateurs;
    private long totalCitoyens;
    private long totalAgents;
    private long totalResponsables;
    private long totalDossiers;
    private Map<String, Long> dossiersParStatut;
    private Map<String, Long> dossiersParOrganisation;
}
