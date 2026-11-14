package cm.civica.backend.dto;

import cm.civica.backend.enums.StatutDossier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoriqueDossierDto {
    private Long id;
    private String agentNom;
    private String serviceSourceNom;
    private String serviceCibleNom;
    private StatutDossier statutPrecedent;
    private StatutDossier nouveauStatut;
    private String actionEffectuee;
    private String commentaire;
    private Instant timestamp;
}

