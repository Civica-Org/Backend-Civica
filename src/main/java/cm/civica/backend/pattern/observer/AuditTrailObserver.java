package cm.civica.backend.pattern.observer;

import cm.civica.backend.model.HistoriqueDossier;
import cm.civica.backend.repository.HistoriqueDossierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Observateur concret : Audit et traçabilité (Pattern GoF Observer).
 * Enregistre chaque transition de cycle de vie dans la table historique pour conformité légale.
 */
@Component
public class AuditTrailObserver implements DossierObserver {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailObserver.class);

    @Autowired
    private HistoriqueDossierRepository historiqueRepository;

    @Override
    public void onDossierEvent(DossierEvent event) {
        if (event.getDossier() == null) return;

        HistoriqueDossier historique = HistoriqueDossier.builder()
                .dossier(event.getDossier())
                .agent(event.getActeur())
                .serviceSource(event.getServiceConcerne() != null ? event.getServiceConcerne() : event.getDossier().getServiceCourant())
                .statutPrecedent(event.getAncienStatut())
                .nouveauStatut(event.getNouveauStatut())
                .actionEffectuee(event.getAction())
                .commentaire(event.getCommentaire())
                .timestamp(event.getTimestamp())
                .build();

        historiqueRepository.save(historique);
        log.debug("[AUDIT OBSERVER] Entrée d'historique persistée avec succès pour {}", event.getDossier().getNumeroSuivi());
    }
}
