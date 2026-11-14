package cm.civica.backend.pattern.chain;

import cm.civica.backend.model.Dossier;

/**
 * Interface du Pattern Chain of Responsibility — GoF (Comportemental).
 *
 * Chaque maillon de la chaîne effectue une vérification sur un dossier avant
 * qu'une décision (validation/rejet) ne soit rendue.
 *
 * La chaîne standard pour CIVICA est :
 *   VerificationPresencePieces → VerificationConformitePieces → VerificationDelaiTraitement
 *
 * Si un maillon détecte un problème, il retourne un résultat négatif.
 * Sinon, il passe au maillon suivant via passerAuSuivant().
 */
public abstract class ValidateurDossier {

    private ValidateurDossier suivant;

    /**
     * Enchaîne ce validateur avec le suivant et retourne le suivant
     * (permet l'écriture fluide : validateur1.puis(validateur2).puis(validateur3))
     */
    public ValidateurDossier puis(ValidateurDossier suivant) {
        this.suivant = suivant;
        return suivant;
    }

    /** Délègue au maillon suivant ou retourne succès si fin de chaîne. */
    protected ResultatValidation passerAuSuivant(Dossier dossier) {
        if (suivant == null) {
            return ResultatValidation.succes();
        }
        return suivant.valider(dossier);
    }

    /** Point d'entrée de la validation. Chaque sous-classe implémente sa règle ici. */
    public abstract ResultatValidation valider(Dossier dossier);
}

