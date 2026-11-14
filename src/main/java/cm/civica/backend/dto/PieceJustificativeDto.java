package cm.civica.backend.dto;

import cm.civica.backend.enums.StatutPiece;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PieceJustificativeDto {
    private Long id;
    private String intitule;
    private String typeMime;
    private String nomFichierStocke;
    private Long tailleOctets;
    private StatutPiece statutValidation;
    private String observationsIa;
    private String urlTelechargement;
    private Instant uploadedAt;
}

