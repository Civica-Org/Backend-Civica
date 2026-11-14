package cm.civica.backend.pattern.state;

import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.model.Dossier;

/** État RETIRE : terminal — le citoyen a retiré sa demande. */
public class EtatRetire implements EtatDossier {
    @Override
    public StatutDossier getStatut() {
        return StatutDossier.RETIRE;
    }
}

