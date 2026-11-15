package cm.civica.backend.pattern.observer;

/**
 * Interface Subject / Observable (Pattern GoF Observer).
 * Définit le contrat d'enregistrement et de notification des observateurs.
 */
public interface DossierSubject {

    void registerObserver(DossierObserver observer);

    void removeObserver(DossierObserver observer);

    void notifyObservers(DossierEvent event);
}
