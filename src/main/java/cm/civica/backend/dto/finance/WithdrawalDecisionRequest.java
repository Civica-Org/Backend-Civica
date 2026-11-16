package cm.civica.backend.dto.finance;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalDecisionRequest {

    @Size(max = 500, message = "Le commentaire ne doit pas dépasser 500 caractères")
    private String commentaireDecision;
}
