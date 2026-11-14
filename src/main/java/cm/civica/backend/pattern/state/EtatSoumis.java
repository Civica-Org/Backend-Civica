package cm.civica.backend.pattern.state;

import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.model.Dossier;

/** État SOUMIS : le citoyen a déposé, le dossier attend d'être pris en charge. */
public class EtatSoumis implements EtatDossier {

    @Override
    public void prendreEnCharge(Dossier dossier) {
        dossier.setStatut(StatutDossier.EN_VERIFICATION);
    }

    @Override
    public void transferer(Dossier dossier) {
        dossier.setStatut(StatutDossier.TRANSFERE);
    }

    @Override
    public void rejeter(Dossier dossier, String motif) {
        dossier.setStatut(StatutDossier.REJETE);
        dossier.setMotifRejet(motif);
    }

    @Override
    public void retirer(Dossier dossier) {
        dossier.setStatut(StatutDossier.RETIRE);
    }

    @Override
    public StatutDossier getStatut() {
        return StatutDossier.SOUMIS;
    }
}

