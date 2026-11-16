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
public class PaiementDto {
    private Long id;
    private String referenceTransaction;
    private String quittanceNumero;
    private String numeroSuivi;
    private String demarcheCode;
    private String demarcheTitre;
    private Long serviceId;
    private String citoyenNomComplet;
    private BigDecimal montantTotal;
    private String devise;
    private String modePaiement;
    private String instrumentMasque;
    private String varianteCircuit;
    private String statut;
    private Instant createdAt;
    private Instant confirmedAt;

    @Builder.Default
    private List<PaymentAllocationDto> allocations = new ArrayList<>();
}
