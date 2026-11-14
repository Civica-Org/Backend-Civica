package cm.civica.backend.pattern.state;

import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.exception.BusinessException;
import cm.civica.backend.model.Dossier;

/**
 * Interface du Pattern State — GoF (Comportemental).
 *
 * Chaque implémentation représente un état concret du cycle de vie d'un dossier
 * (BROUILLON, SOUMIS, EN_VERIFICATION, TRANSFERE, VALIDE, REJETE, RETIRE).
 *
 * L'objet contexte (Dossier) délègue toutes les transitions à son état courant.
 * Cela évite une cascade de if/switch sur StatutDossier dans les services
 * et rend chaque transition explicite, testable et extensible.
 */
public interface EtatDossier {

    /** Soumettre le dossier pour instruction */
    default void soumettre(Dossier dossier) {
        throw BusinessException.badRequest(
            "[STATE] Impossible de soumettre : le dossier est en état '" + dossier.getStatut() + "'.");
    }

    /** L'agent prend le dossier en charge */
    default void prendreEnCharge(Dossier dossier) {
        throw BusinessException.badRequest(
            "[STATE] Impossible de prendre en charge : le dossier est en état '" + dossier.getStatut() + "'.");
    }

    /** Transférer vers un autre service */
    default void transferer(Dossier dossier) {
        throw BusinessException.badRequest(
            "[STATE] Impossible de transférer : le dossier est en état '" + dossier.getStatut() + "'.");
    }

    /** Valider le dossier */
    default void valider(Dossier dossier) {
        throw BusinessException.badRequest(
            "[STATE] Impossible de valider : le dossier est en état '" + dossier.getStatut() + "'.");
    }

    /** Rejeter le dossier (motif obligatoire) */
    default void rejeter(Dossier dossier, String motif) {
        throw BusinessException.badRequest(
            "[STATE] Impossible de rejeter : le dossier est en état '" + dossier.getStatut() + "'.");
    }

    /** Retirer le dossier (par le citoyen) */
    default void retirer(Dossier dossier) {
        throw BusinessException.badRequest(
            "[STATE] Impossible de retirer : le dossier est en état '" + dossier.getStatut() + "'.");
    }

    /** Retourne l'identifiant de l'état courant */
    StatutDossier getStatut();
}

