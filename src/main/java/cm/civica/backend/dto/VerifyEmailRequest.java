package cm.civica.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyEmailRequest {

    @NotBlank(message = "L'adresse email ou l'identifiant est obligatoire")
    private String email;

    @NotBlank(message = "Le code de vérification est obligatoire")
    private String code;
}
