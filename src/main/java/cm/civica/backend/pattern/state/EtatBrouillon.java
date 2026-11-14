package cm.civica.backend.pattern.state;

import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.model.Dossier;

/** État BROUILLON : seule la soumission est autorisée. */
public class EtatBrouillon implements EtatDossier {

    @Override
    public void soumettre(Dossier dossier) {
        dossier.setStatut(StatutDossier.SOUMIS);
    }

    @Override
    public void retirer(Dossier dossier) {
        dossier.setStatut(StatutDossier.RETIRE);
    }

    @Override
    public StatutDossier getStatut() {
        return StatutDossier.BROUILLON;
    }
}

