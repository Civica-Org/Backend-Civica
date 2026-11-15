package cm.civica.backend.pattern.observer;

/**
 * Interface Observer (Pattern GoF Observer).
 * Tout composant souhaitant réagir aux mutations d'un dossier
 * doit implémenter cette interface.
 */
public interface DossierObserver {

    /**
     * Méthode appelée automatiquement par le Subject lors d'une mutation.
     *
     * @param event Événement décrivant le changement intervenu sur le dossier
     */
    void onDossierEvent(DossierEvent event);
}
