package cm.civica.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepotDossierRequest {

    @NotBlank(message = "Le code de la démarche est obligatoire")
    private String demarcheCode;

    private Long serviceId;

    private String priorite = "NORMALE";

    /** Variante réglementaire éventuelle (ex. Acte de naissance : EXTRAIT_COPIE, NORMALE, TARDIVE, JUDICIAIRE). */
    private String varianteCircuit;

    /** Référence de transaction confirmée avant soumission (obligatoire si les frais > 0 XAF). */
    private String paiementReference;
}

