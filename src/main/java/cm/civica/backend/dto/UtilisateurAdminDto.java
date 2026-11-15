package cm.civica.backend.dto;

import cm.civica.backend.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UtilisateurAdminDto {
    private Long id;
    private String email;
    private String nom;
    private String prenom;
    private String telephone;
    private Role role;
    private Boolean actif;
    private Boolean emailVerifie;
    private Long serviceId;
    private String serviceNom;
    private String organisationNom;
    private Instant createdAt;
}
