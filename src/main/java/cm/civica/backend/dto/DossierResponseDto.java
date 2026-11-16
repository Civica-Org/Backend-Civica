package cm.civica.backend.dto;

import cm.civica.backend.dto.finance.PaiementDto;
import cm.civica.backend.enums.StatutDossier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DossierResponseDto {
    private Long id;
    private String numeroSuivi;
    private Long citoyenId;
    private String citoyenNomComplet;
    private String citoyenEmail;
    private String citoyenTelephone;
    private String demarcheCode;
    private String demarcheTitre;
    private Integer delaiIndicatifJours;
    private Long serviceCourantId;
    private String serviceCourantNom;
    private String organisationNom;
    private StatutDossier statut;
    private String priorite;
    private String motifRejet;
    private Instant createdAt;
    private Instant updatedAt;

    /** Indique si les droits légaux du dossier ont été acquittés et quittancés. */
    private boolean paye;

    /** Détails complets du paiement confirmé (quittance, moyen de paiement, répartition) s'il existe. */
    private PaiementDto paiementConfirme;

    @Builder.Default
    private List<PieceJustificativeDto> pieces = new ArrayList<>();

    @Builder.Default
    private List<HistoriqueDossierDto> historiques = new ArrayList<>();
}

