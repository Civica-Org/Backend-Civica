package cm.civica.backend.dto.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BaremePaiementDto {
    private String demarcheCode;
    private String demarcheTitre;
    private String varianteCircuit;
    private BigDecimal montantTotal;
    private String devise;
    private boolean gratuit;
    private String referenceLegale;

    @Builder.Default
    private List<AllocationRuleDto> repartitionPrevue = new ArrayList<>();
}
