package cm.civica.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO regroupant les indicateurs clés de performance (KPIs)
 * pour le tableau de bord de supervision du Responsable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponsableDto {

    private String codeService;
    private String nomService;
    private String nomOrganisation;

    // Métriques globales
    private long totalDossiers;
    private long dossiersEnAttente;      // Statut SOUMIS
    private long dossiersEnCours;        // Statut EN_VERIFICATION
    private long dossiersValides;        // Statut VALIDE
    private long dossiersRejetes;        // Statut REJETE
    private long dossiersTransferes;     // Statut TRANSFERE

    // Indicateurs calculés
    private double tauxValidation;       // % de validation
    private double tauxRejet;            // % de rejet
    private double delaiMoyenJours;      // Délai moyen d'instruction
    private long nombreDossiersEnRetard; // Dossiers dépassant le délai légal

    // Répartition par démarche
    private Map<String, Long> repartitionParDemarche;

    // Charge de travail des agents du service
    private List<AgentChargeDto> agentsCharge;

    // Liste des dossiers urgents ou en souffrance
    private List<DossierResponseDto> dossiersEnSouffrance;
}

