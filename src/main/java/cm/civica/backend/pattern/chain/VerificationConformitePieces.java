package cm.civica.backend.pattern.chain;

import cm.civica.backend.enums.StatutPiece;
import cm.civica.backend.model.Dossier;
import cm.civica.backend.model.PieceJustificative;
import org.springframework.stereotype.Component;

/**
 * Maillon 2 : Vérification de la conformité des pièces.
 * Si une pièce a été explicitement marquée NON_CONFORME, le dossier ne peut être validé.
 */
@Component
public class VerificationConformitePieces extends ValidateurDossier {

    @Override
    public ResultatValidation valider(Dossier dossier) {
        if (dossier.getPieces() != null) {
            for (PieceJustificative piece : dossier.getPieces()) {
                if (piece.getStatutValidation() == StatutPiece.NON_CONFORME) {
                    return ResultatValidation.echec(
                            "Pièce non conforme détectée : '" + piece.getIntitule() +
                            "'. Observations : " + (piece.getObservationsIa() != null ? piece.getObservationsIa() : "Non spécifié"));
                }
            }
        }
        return passerAuSuivant(dossier);
    }
}

