package cm.civica.backend.pattern.state;

import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.model.Dossier;

/** État TRANSFERE : le dossier est en transit vers un autre service. */
public class EtatTransfere implements EtatDossier {

    @Override
    public void prendreEnCharge(Dossier dossier) {
        dossier.setStatut(StatutDossier.EN_VERIFICATION);
    }

    @Override
    public void transferer(Dossier dossier) {
        // Nouveau transfert en chaîne autorisé
        dossier.setStatut(StatutDossier.TRANSFERE);
    }

    @Override
    public void rejeter(Dossier dossier, String motif) {
        dossier.setStatut(StatutDossier.REJETE);
        dossier.setMotifRejet(motif);
    }

    @Override
    public StatutDossier getStatut() {
        return StatutDossier.TRANSFERE;
    }
}

