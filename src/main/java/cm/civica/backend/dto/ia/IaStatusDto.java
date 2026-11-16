package cm.civica.backend.dto.ia;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IaStatusDto {
    private boolean geminiConfigure;
    private String fournisseurActif;
    private String modelePrincipal;
    private String modeleFallback;
    private long timeoutMs;
    private String regleCardinale;
}

