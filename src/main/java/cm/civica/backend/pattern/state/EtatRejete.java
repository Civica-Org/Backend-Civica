package cm.civica.backend.pattern.state;

import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.model.Dossier;

/** État REJETE : le citoyen peut déposer un nouveau dossier, mais celui-ci ne bouge plus. */
public class EtatRejete implements EtatDossier {
    @Override
    public StatutDossier getStatut() {
        return StatutDossier.REJETE;
    }
}

