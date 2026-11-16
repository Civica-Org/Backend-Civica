package cm.civica.backend.dto.ia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CivicaIaChatRequest {

    @NotBlank(message = "Veuillez saisir votre question pour Civica IA.")
    @Size(max = 1500, message = "La question ne doit pas dépasser 1500 caractères.")
    private String question;

    @Size(max = 80)
    private String numeroSuivi;

    @Size(max = 60)
    private String demarcheCode;

    @Size(max = 120)
    private String pageContexte;

    @Valid
    @Builder.Default
    private List<CivicaIaMessageDto> historique = new ArrayList<>();
}

