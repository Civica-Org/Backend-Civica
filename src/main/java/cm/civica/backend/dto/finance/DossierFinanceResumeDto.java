package cm.civica.backend.dto.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DossierFinanceResumeDto {
    private String numeroSuivi;
    private boolean dejaPaye;
    private BaremePaiementDto bareme;
    private PaiementDto paiementConfirme;

    @Builder.Default
    private List<PaiementDto> historiquePaiements = new ArrayList<>();
}
