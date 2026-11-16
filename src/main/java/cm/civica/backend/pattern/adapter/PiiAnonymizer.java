package cm.civica.backend.pattern.adapter;

import cm.civica.backend.model.Dossier;
import cm.civica.backend.model.Utilisateur;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Filtre d'anonymisation (Chapitres 8 & 20) exécuté avant tout appel à l'API externe Gemini.
 * Supprime ou masque les identifiants réels, noms, emails, numéros de téléphone et références personnelles
 * afin qu'aucune donnée à caractère personnel ne quitte le périmètre CIVICA.
 */
@Component
public class PiiAnonymizer {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?:\\+?237[\\s.\\-]?)?(?:6[0-9]{2}[\\s.\\-]?[0-9]{2}[\\s.\\-]?[0-9]{2}[\\s.\\-]?[0-9]{2}|\\b[0-9]{9,13}\\b)");

    private static final Pattern TRACKING_PATTERN =
            Pattern.compile("\\bCIVICA-[0-9]{4}-[0-9]{2}-[0-9]{2}-DOS-[0-9]+\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern IBAN_OR_ACCOUNT_PATTERN =
            Pattern.compile("\\b(?:CM[0-9]{2}|FR[0-9]{2})[A-Z0-9\\-]{8,30}\\b", Pattern.CASE_INSENSITIVE);

    public String sanitizeText(String rawText) {
        return sanitizeText(rawText, null);
    }

    public String sanitizeText(String rawText, Dossier dossier) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String sanitized = rawText;

        if (dossier != null) {
            if (dossier.getNumeroSuivi() != null && !dossier.getNumeroSuivi().isBlank()) {
                sanitized = sanitized.replace(dossier.getNumeroSuivi(), "[DOSSIER_ANONYME]");
            }
            Utilisateur citoyen = dossier.getCitoyen();
            if (citoyen != null) {
                sanitized = replaceIgnoreCase(sanitized, citoyen.getEmail(), "[EMAIL_ANONYME]");
                sanitized = replaceIgnoreCase(sanitized, citoyen.getTelephone(), "[TEL_ANONYME]");
                String fullName = ((citoyen.getPrenom() != null ? citoyen.getPrenom() : "") + " " +
                        (citoyen.getNom() != null ? citoyen.getNom() : "")).trim();
                sanitized = replaceIgnoreCase(sanitized, fullName, "[USAGER_ANONYME]");
                sanitized = replaceIgnoreCase(sanitized, citoyen.getNom(), "[NOM_ANONYME]");
                sanitized = replaceIgnoreCase(sanitized, citoyen.getPrenom(), "[PRENOM_ANONYME]");
            }
        }

        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("[EMAIL_ANONYME]");
        sanitized = TRACKING_PATTERN.matcher(sanitized).replaceAll("[DOSSIER_ANONYME]");
        sanitized = IBAN_OR_ACCOUNT_PATTERN.matcher(sanitized).replaceAll("[COMPTE_ANONYME]");
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("[TEL_ANONYME]");

        return sanitized.trim();
    }

    private String replaceIgnoreCase(String text, String target, String replacement) {
        if (target == null || target.isBlank() || target.trim().length() < 2) {
            return text;
        }
        return text.replaceAll("(?i)" + Pattern.quote(target.trim()), replacement);
    }
}

