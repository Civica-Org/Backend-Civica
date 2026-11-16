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
    private long organisationsActives;
    private long totalServices;
    private long servicesActifs;
    private long totalDemarches;
    private long demarchesActives;
    private long totalUtilisateurs;
    private long totalCitoyens;
    private long totalAgents;
    private long totalSousResponsables;
    private long totalResponsables;
    private Map<String, Long> organisationsParType;
    private Map<String, Long> servicesParOrganisation;
}
