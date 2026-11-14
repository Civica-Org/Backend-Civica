package cm.civica.backend.pattern.state;

import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.model.Dossier;

/** État VALIDE : terminal positif — aucune transition autorisée. */
public class EtatValide implements EtatDossier {
    @Override
    public StatutDossier getStatut() {
        return StatutDossier.VALIDE;
    }
}

