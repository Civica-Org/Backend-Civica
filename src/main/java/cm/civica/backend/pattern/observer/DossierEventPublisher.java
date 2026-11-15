package cm.civica.backend.pattern.observer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implémentation concrète du Subject (Pattern GoF Observer).
 * Maintient la liste des observateurs et relaie chaque événement survenu sur un dossier.
 */
@Component
public class DossierEventPublisher implements DossierSubject {

    private static final Logger log = LoggerFactory.getLogger(DossierEventPublisher.class);

    private final List<DossierObserver> observers = new CopyOnWriteArrayList<>();

    /**
     * Injection automatique de tous les observateurs déclarés comme beans Spring.
     */
    @Autowired
    public DossierEventPublisher(List<DossierObserver> initialObservers) {
        if (initialObservers != null) {
            this.observers.addAll(initialObservers);
            log.info("[PATTERN OBSERVER] Initialisé avec {} observateurs enregistrés.", initialObservers.size());
        }
    }

    @Override
    public void registerObserver(DossierObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
            log.debug("[PATTERN OBSERVER] Nouvel observateur enregistré : {}", observer.getClass().getSimpleName());
        }
    }

    @Override
    public void removeObserver(DossierObserver observer) {
        observers.remove(observer);
        log.debug("[PATTERN OBSERVER] Observateur retiré : {}", observer.getClass().getSimpleName());
    }

    @Override
    public void notifyObservers(DossierEvent event) {
        if (event == null) return;
        log.info("[PATTERN OBSERVER] Diffusion de l'événement '{}' pour le dossier {} (statut: {} -> {}) à {} observateurs.",
                event.getAction(),
                event.getDossier() != null ? event.getDossier().getNumeroSuivi() : "N/A",
                event.getAncienStatut(),
                event.getNouveauStatut(),
                observers.size());

        for (DossierObserver observer : observers) {
            try {
                observer.onDossierEvent(event);
            } catch (Exception e) {
                log.error("[PATTERN OBSERVER] Erreur lors de la notification de l'observateur {} : {}",
                        observer.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }
}
