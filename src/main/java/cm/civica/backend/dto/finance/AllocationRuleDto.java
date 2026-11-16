package cm.civica.backend.dto.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllocationRuleDto {
    private String organisationCode;
    private String organisationNom;
    private String libelleQuotePart;
    private BigDecimal pourcentage;
    private BigDecimal montantPrevu;
}
