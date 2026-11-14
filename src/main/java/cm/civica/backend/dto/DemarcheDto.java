package cm.civica.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemarcheDto {
    private Long id;
    private String code;
    private String titre;
    private String description;
    private Integer delaiIndicatifJours;
    private Boolean actif;
}

