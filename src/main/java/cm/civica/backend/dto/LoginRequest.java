package cm.civica.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    // Accepte soit l'adresse email, soit le numéro de téléphone (+237 6...)
    private String identifiant;

    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    private String password;

    public String getIdentifiant() {
        if (identifiant != null && !identifiant.isBlank()) {
            return identifiant.trim();
        }
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        return "";
    }
}
