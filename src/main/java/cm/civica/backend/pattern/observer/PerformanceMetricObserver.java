package cm.civica.backend.pattern.observer;

import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.model.Dossier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Observateur concret : Télémétrie et indicateurs de performance (Pattern GoF Observer).
 * Mesure les temps de traitement et relève les dépassements de délais indicatifs.
 */
@Component
public class PerformanceMetricObserver implements DossierObserver {

    private static final Logger log = LoggerFactory.getLogger(PerformanceMetricObserver.class);

    @Override
    public void onDossierEvent(DossierEvent event) {
        Dossier dossier = event.getDossier();
        if (dossier == null) return;

        StatutDossier statut = event.getNouveauStatut();

        if (statut == StatutDossier.VALIDE || statut == StatutDossier.REJETE) {
            Instant dateDepot = dossier.getCreatedAt();
            Instant dateFin = event.getTimestamp();

            if (dateDepot != null && dateFin != null) {
                Duration duree = Duration.between(dateDepot, dateFin);
                long heures = duree.toHours();
                long jours = duree.toDays();

                log.info("[KPI METRIC] Dossier {} clos avec statut {} en {} jours ({} heures).",
                        dossier.getNumeroSuivi(), statut, jours, heures);

                if (dossier.getDemarche() != null && dossier.getDemarche().getDelaiIndicatifJours() != null) {
                    int delaiMaxJours = dossier.getDemarche().getDelaiIndicatifJours();
                    if (jours > delaiMaxJours) {
                        log.warn("[KPI ALERTE RETARD] Le dossier {} a dépassé le délai indicatif de {} jours (délai réel: {} jours) !",
                                dossier.getNumeroSuivi(), delaiMaxJours, jours);
                    }
                }
            }
        }
    }
}
