package cm.civica.backend.pattern.chain;

import cm.civica.backend.model.Dossier;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Orchestrateur du Pattern Chain of Responsibility (Circuit de validation).
 *
 * Construit la chaîne de validateurs :
 * PresencePieces -> ConformitePieces -> ServiceCompetent
 */
@Component
public class CircuitValidationDossier {

    @Autowired
    private VerificationPresencePieces presencePieces;

    @Autowired
    private VerificationConformitePieces conformitePieces;

    @Autowired
    private VerificationServiceCompetent serviceCompetent;

    private ValidateurDossier premierMaillon;

    @PostConstruct
    public void initialiserChaine() {
        presencePieces.puis(conformitePieces).puis(serviceCompetent);
        this.premierMaillon = presencePieces;
    }

    public ResultatValidation executerCircuit(Dossier dossier) {
        if (premierMaillon == null) {
            initialiserChaine();
        }
        return premierMaillon.valider(dossier);
    }
}

