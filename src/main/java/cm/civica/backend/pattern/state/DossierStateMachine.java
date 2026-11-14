package cm.civica.backend.pattern.state;

import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.exception.BusinessException;
import cm.civica.backend.model.Dossier;
import cm.civica.backend.model.ServiceAdministratif;
import org.springframework.stereotype.Component;

/**
 * Contexte du Pattern State — GoF (Comportemental).
 *
 * Ce composant fait le lien entre le Dossier (entité JPA) et les implémentations
 * concrètes d'EtatDossier. Toutes les transitions métier passent obligatoirement
 * par cette classe. Aucun service ne doit modifier dossier.setStatut() directement.
 *
 * Exemple d'utilisation dans AgentService :
 *   dossierStateMachine.prendreEnCharge(dossier);
 *   dossierStateMachine.valider(dossier);
 *   dossierStateMachine.rejeter(dossier, "Pièce manquante");
 */
@Component
public class DossierStateMachine {

    /**
     * Résout l'état concret du dossier à partir de son StatutDossier persisté.
     */
    public EtatDossier resolveEtat(Dossier dossier) {
        return switch (dossier.getStatut()) {
            case BROUILLON      -> new EtatBrouillon();
            case SOUMIS         -> new EtatSoumis();
            case EN_VERIFICATION -> new EtatEnVerification();
            case TRANSFERE      -> new EtatTransfere();
            case VALIDE         -> new EtatValide();
            case REJETE         -> new EtatRejete();
            case RETIRE         -> new EtatRetire();
        };
    }

    /** Soumet un dossier brouillon. */
    public void soumettre(Dossier dossier) {
        resolveEtat(dossier).soumettre(dossier);
    }

    /** L'agent prend en charge : SOUMIS / TRANSFERE → EN_VERIFICATION */
    public void prendreEnCharge(Dossier dossier) {
        resolveEtat(dossier).prendreEnCharge(dossier);
    }

    /** Transfère le dossier vers un autre service. */
    public void transferer(Dossier dossier, ServiceAdministratif cible) {
        resolveEtat(dossier).transferer(dossier);
        // Respect invariant formel Alloy : un seul service détenteur à la fois
        dossier.setServiceCourant(cible);
    }

    /** Valide le dossier : EN_VERIFICATION → VALIDE */
    public void valider(Dossier dossier) {
        resolveEtat(dossier).valider(dossier);
    }

    /** Rejette le dossier avec motif obligatoire. */
    public void rejeter(Dossier dossier, String motif) {
        if (motif == null || motif.isBlank()) {
            throw BusinessException.badRequest("Un motif de rejet explicite est obligatoire.");
        }
        resolveEtat(dossier).rejeter(dossier, motif);
    }

    /** Retire le dossier (action citoyen). */
    public void retirer(Dossier dossier) {
        resolveEtat(dossier).retirer(dossier);
    }

    /** Vérifie si une transition vers un statut est légalement autorisée. */
    public boolean peutTransitionnerVers(Dossier dossier, StatutDossier cible) {
        try {
            Dossier copie = new Dossier();
            copie.setStatut(dossier.getStatut());
            switch (cible) {
                case SOUMIS         -> resolveEtat(copie).soumettre(copie);
                case EN_VERIFICATION -> resolveEtat(copie).prendreEnCharge(copie);
                case TRANSFERE      -> resolveEtat(copie).transferer(copie);
                case VALIDE         -> resolveEtat(copie).valider(copie);
                case REJETE         -> resolveEtat(copie).rejeter(copie, "test");
                case RETIRE         -> resolveEtat(copie).retirer(copie);
                default -> { return false; }
            }
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }
}

