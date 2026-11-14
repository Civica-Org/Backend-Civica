package cm.civica.backend.pattern.state;

import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.model.Dossier;

/** État EN_VERIFICATION : un agent instruit le dossier. */
public class EtatEnVerification implements EtatDossier {

    @Override
    public void valider(Dossier dossier) {
        dossier.setStatut(StatutDossier.VALIDE);
        dossier.setMotifRejet(null);
    }

    @Override
    public void rejeter(Dossier dossier, String motif) {
        dossier.setStatut(StatutDossier.REJETE);
        dossier.setMotifRejet(motif);
    }

    @Override
    public void transferer(Dossier dossier) {
        dossier.setStatut(StatutDossier.TRANSFERE);
    }

    @Override
    public StatutDossier getStatut() {
        return StatutDossier.EN_VERIFICATION;
    }
}

