package cm.civica.backend.pattern.observer;

import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.model.Dossier;
import cm.civica.backend.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Observateur concret : Notifications citoyennes (Pattern GoF Observer).
 * Déclenche les alertes et emails transactionnels en réaction aux événements métier.
 */
@Component
public class NotificationObserver implements DossierObserver {

    private static final Logger log = LoggerFactory.getLogger(NotificationObserver.class);

    @Autowired
    private EmailService emailService;

    @Override
    public void onDossierEvent(DossierEvent event) {
        Dossier dossier = event.getDossier();
        if (dossier == null || dossier.getCitoyen() == null) return;

        StatutDossier nouveauStatut = event.getNouveauStatut();

        if (nouveauStatut == StatutDossier.SOUMIS) {
            emailService.sendConfirmationDepot(dossier);
        } else if (nouveauStatut == StatutDossier.EN_VERIFICATION) {
            emailService.sendNotificationChangementStatut(dossier, "Votre dossier a été pris en charge pour instruction.");
        } else if (nouveauStatut == StatutDossier.VALIDE) {
            emailService.sendNotificationChangementStatut(dossier, "Félicitations ! Votre dossier a été validé par l'administration.");
        } else if (nouveauStatut == StatutDossier.REJETE) {
            emailService.sendNotificationChangementStatut(dossier, "Votre dossier a été rejeté. Motif : " + event.getCommentaire());
        } else if (nouveauStatut == StatutDossier.TRANSFERE) {
            emailService.sendNotificationChangementStatut(dossier, "Votre dossier a été transféré vers un service compétent pour suite de traitement.");
        } else if (nouveauStatut == StatutDossier.RETIRE) {
            emailService.sendNotificationChangementStatut(dossier, "Le document final relatif à votre démarche a été retiré/délivré avec succès.");
        }

        log.debug("[NOTIFICATION OBSERVER] Traitement notification terminé pour {}", dossier.getNumeroSuivi());
    }
}

