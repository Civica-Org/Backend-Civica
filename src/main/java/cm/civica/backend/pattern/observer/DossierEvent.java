package cm.civica.backend.pattern.observer;

import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.model.Dossier;
import cm.civica.backend.model.ServiceAdministratif;
import cm.civica.backend.model.Utilisateur;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Représente l'événement notifié aux observateurs lors d'une transition
 * de cycle de vie ou d'une opération majeure sur un dossier administratif.
 */
@Getter
@Builder
public class DossierEvent {

    private final Dossier dossier;
    private final StatutDossier ancienStatut;
    private final StatutDossier nouveauStatut;
    private final String action;
    private final String commentaire;
    private final Utilisateur acteur;
    private final ServiceAdministratif serviceConcerne;
    private final Instant timestamp;

    public static DossierEvent of(Dossier dossier, StatutDossier ancienStatut, StatutDossier nouveauStatut,
                                  String action, String commentaire, Utilisateur acteur, ServiceAdministratif service) {
        return DossierEvent.builder()
                .dossier(dossier)
                .ancienStatut(ancienStatut)
                .nouveauStatut(nouveauStatut)
                .action(action)
                .commentaire(commentaire)
                .acteur(acteur)
                .serviceConcerne(service)
                .timestamp(Instant.now())
                .build();
    }
}

