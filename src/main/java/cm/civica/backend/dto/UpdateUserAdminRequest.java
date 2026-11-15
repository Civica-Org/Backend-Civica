package cm.civica.backend.dto;

import cm.civica.backend.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserAdminRequest {
    private Role role;
    private Long serviceId;
    private Boolean actif;
}
