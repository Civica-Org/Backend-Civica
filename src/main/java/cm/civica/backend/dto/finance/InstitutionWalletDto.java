package cm.civica.backend.dto.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstitutionWalletDto {
    private Long id;
    private Long organisationId;
    private String organisationCode;
    private String organisationNom;
    private String typeOrganisation;
    private String numeroCompteTresor;
    private BigDecimal soldeDisponible;
    private BigDecimal soldeBloque;
    private BigDecimal totalEncaisse;
    private BigDecimal totalDecaisse;
    private String devise;
    private Instant updatedAt;

    @Builder.Default
    private List<PaymentAllocationDto> allocationsRecentes = new ArrayList<>();
}
