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
public class PaymentAllocationDto {
    private Long id;
    private Long paiementId;
    private String referenceTransaction;
    private String numeroSuivi;
    private Long walletId;
    private Long organisationId;
    private String organisationCode;
    private String organisationNom;
    private String libelleQuotePart;
    private BigDecimal pourcentage;
    private BigDecimal montantAlloue;
    private String devise;
    private Instant createdAt;
}
