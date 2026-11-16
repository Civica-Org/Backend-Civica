package cm.civica.backend.dto.ia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class PrecheckCompletudeRequest {

    @NotBlank(message = "Le code de démarche est obligatoire.")
    @Size(max = 60)
    private String demarcheCode;

    @Size(max = 80)
    private String varianteCircuit;

    @Builder.Default
    private List<String> intitulesPieces = new ArrayList<>();
}

