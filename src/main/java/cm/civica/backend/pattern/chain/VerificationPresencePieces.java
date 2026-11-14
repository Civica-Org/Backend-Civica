package cm.civica.backend.pattern.chain;

import cm.civica.backend.model.Dossier;
import org.springframework.stereotype.Component;

/**
 * Maillon 1 : Vérification de la présence effective de pièces justificatives.
 * Un dossier sans aucune pièce ne peut être validé.
 */
@Component
public class VerificationPresencePieces extends ValidateurDossier {

    @Override
    public ResultatValidation valider(Dossier dossier) {
        if (dossier.getPieces() == null || dossier.getPieces().isEmpty()) {
            return ResultatValidation.echec("Dossier incomplet : aucune pièce justificative n'a été fournie.");
        }
        return passerAuSuivant(dossier);
    }
}

