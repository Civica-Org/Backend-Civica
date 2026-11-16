package cm.civica.backend.dto;

import cm.civica.backend.enums.Role;
import cm.civica.backend.model.Organisation;
import cm.civica.backend.model.Utilisateur;
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
    private Long organisationId;
    private String organisationNom;
    private Instant createdAt;

    /** Construit un DTO plat et sûr pour la sérialisation JSON depuis une entité Utilisateur. */
    public static UtilisateurAdminDto fromEntity(Utilisateur u) {
        Long serviceId = u.getService() != null ? u.getService().getId() : null;
        String serviceNom = u.getService() != null ? u.getService().getNom() : null;
        Organisation org = u.resolveEffectiveOrganisation();
        Long organisationId = org != null ? org.getId() : null;
        String orgNom = org != null ? org.getNom() : null;

        return UtilisateurAdminDto.builder()
                .id(u.getId())
                .email(u.getEmail())
                .nom(u.getNom())
                .prenom(u.getPrenom())
                .telephone(u.getTelephone())
                .role(u.getRole())
                .actif(u.getActif())
                .emailVerifie(u.getEmailVerifie())
                .serviceId(serviceId)
                .serviceNom(serviceNom)
                .organisationId(organisationId)
                .organisationNom(orgNom)
                .createdAt(u.getCreatedAt())
                .build();
    }
}
