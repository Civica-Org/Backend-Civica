package cm.civica.backend.dto.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InitierPaiementRequest {

    /** Renseigné pour régler un dossier existant ; omis lors du paiement obligatoire avant soumission. */
    private String numeroSuivi;

    /** Code de la démarche (obligatoire lors du paiement préalable avant soumission). */
    private String demarcheCode;

    /** Service administratif sélectionné par le citoyen (oriente la quote-part institutionnelle). */
    private Long serviceId;

    @NotBlank(message = "Le mode de paiement est obligatoire")
    @Pattern(
        regexp = "MOMO|OM|CARTE_BANCAIRE|COMPTE_BANCAIRE",
        message = "Mode de paiement non supporté (MOMO, OM, CARTE_BANCAIRE, COMPTE_BANCAIRE)"
    )
    private String modePaiement;

    private String varianteCircuit;

    private String numeroInstrument;

    private Boolean simulerEchec;
}
