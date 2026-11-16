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
public class FinanceOverviewDto {
    private BigDecimal totalEncaisseGlobal;
    private BigDecimal totalDisponibleGlobal;
    private BigDecimal totalBloqueGlobal;
    private BigDecimal totalDecaisseGlobal;
    private long nombrePaiementsConfirmes;
    private long nombreRetraitsEnAttente;

    @Builder.Default
    private List<InstitutionWalletDto> wallets = new ArrayList<>();

    @Builder.Default
    private List<WithdrawalRequestDto> retraits = new ArrayList<>();

    @Builder.Default
    private List<PaymentAllocationDto> allocationsRecentes = new ArrayList<>();

    @Builder.Default
    private List<PaiementDto> paiementsRecents = new ArrayList<>();
}
