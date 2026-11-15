package cm.civica.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceAdminDto {
    private Long id;
    private String code;
    private String nom;
    private Boolean actif;
    private Long organisationId;
    private String organisationCode;
    private String organisationNom;
}
