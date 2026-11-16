package cm.civica.backend.dto.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWithdrawalRequest {

    private Long walletId;

    @NotNull(message = "Le montant du retrait est obligatoire")
    @DecimalMin(value = "100.00", message = "Le montant minimal d'un décaissement est de 100 XAF")
    private BigDecimal montant;

    @NotBlank(message = "Le compte bénéficiaire (IBAN / Trésor / Mobile) est obligatoire")
    @Size(max = 150, message = "Le compte bénéficiaire ne doit pas dépasser 150 caractères")
    private String compteBeneficiaire;

    @NotBlank(message = "Le motif du retrait budgétaire est obligatoire")
    @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères")
    private String motif;
}
