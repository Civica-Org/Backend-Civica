package cm.civica.backend.dto.ia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CivicaIaMessageDto {

    @NotBlank
    private String role; // "user" | "assistant"

    @NotBlank
    @Size(max = 2000)
    private String content;
}

