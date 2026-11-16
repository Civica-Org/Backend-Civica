package cm.civica.backend.dto.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalRequestDto {
    private Long id;
    private String referenceDemande;
    private Long walletId;
    private String numeroCompteTresor;
    private Long organisationId;
    private String organisationCode;
    private String organisationNom;
    private BigDecimal montant;
    private String devise;
    private String compteBeneficiaire;
    private String motif;
    private String statut;
    private Long requestedById;
    private String requestedByNom;
    private String requestedByEmail;
    private Long authorizedById;
    private String authorizedByNom;
    private String commentaireDecision;
    private Instant createdAt;
    private Instant decidedAt;
    private boolean canCurrentUserDecide;
    private String fourEyesBlockReason;
}
