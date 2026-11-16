package cm.civica.backend.dto.ia;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReformulateRejectionRequest {

    @Size(max = 1500)
    private String motifTechnique;

    @Size(max = 1000)
    private String contexte;
}

