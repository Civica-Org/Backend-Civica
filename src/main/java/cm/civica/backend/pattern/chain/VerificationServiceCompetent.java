package cm.civica.backend.pattern.chain;

import cm.civica.backend.model.Dossier;
import org.springframework.stereotype.Component;

/**
 * Maillon 3 : Vérification du service détenteur du dossier.
 * Un dossier doit obligatoirement être rattaché à un service actif pour être finalisé.
 */
@Component
public class VerificationServiceCompetent extends ValidateurDossier {

    @Override
    public ResultatValidation valider(Dossier dossier) {
        if (dossier.getServiceCourant() == null || !Boolean.TRUE.equals(dossier.getServiceCourant().getActif())) {
            return ResultatValidation.echec("Le dossier n'est assigné à aucun service administratif actif compétent.");
        }
        return passerAuSuivant(dossier);
    }
}

