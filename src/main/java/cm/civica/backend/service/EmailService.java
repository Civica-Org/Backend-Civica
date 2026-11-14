package cm.civica.backend.service;

import cm.civica.backend.model.Dossier;
import cm.civica.backend.model.Utilisateur;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:civika.contact@gmail.com}")
    private String fromEmail;

    @Value("${civica.app.frontendUrl:http://localhost:5173}")
    private String frontendUrl;

    /**
     * 1. Code OTP de vérification de l'adresse email lors de l'inscription.
     */
    @Async
    public void sendVerificationEmail(Utilisateur user, String codeOtp) {
        if (user == null || user.getEmail() == null) return;

        String toEmail = user.getEmail();
        String subject = "CIVICA — Code de vérification de votre compte : " + codeOtp;

        String content = "<p>Bonjour <strong>" + user.getPrenom() + " " + user.getNom() + "</strong>,</p>"
                + "<p>Bienvenue sur la plateforme <strong>CIVICA</strong>, le portail des démarches administratives du Cameroun.</p>"
                + "<p>Pour activer votre compte et sécuriser vos futures démarches, veuillez saisir le code de vérification suivant :</p>"
                + "<div style='text-align: center; margin: 30px 0;'>"
                + "  <div style='display: inline-block; background: #f1f5f9; border: 2px dashed #1e3a8a; border-radius: 12px; padding: 18px 36px;'>"
                + "    <span style='font-size: 32px; font-weight: 900; letter-spacing: 8px; color: #1e3a8a; font-family: monospace;'>" + codeOtp + "</span>"
                + "  </div>"
                + "  <p style='margin-top: 12px; font-size: 13px; color: #64748b;'>Ce code est valable pendant <strong>15 minutes</strong>.</p>"
                + "</div>"
                + "<p>Si vous n'êtes pas à l'origine de cette demande, vous pouvez ignorer cet email en toute sécurité.</p>";

        sendHtmlEmail(toEmail, subject, "Votre code de vérification CIVICA", content, null, null);
    }

    /**
     * 2. Alerte de sécurité par email à CHAQUE connexion.
     */
    @Async
    public void sendLoginNotificationEmail(Utilisateur user, String ipAddress, String userAgent) {
        if (user == null || user.getEmail() == null) return;

        String toEmail = user.getEmail();
        String dateHeure = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm:ss"));
        String subject = "CIVICA — Nouvelle connexion détectée à votre compte";

        String content = "<p>Bonjour <strong>" + user.getPrenom() + " " + user.getNom() + "</strong>,</p>"
                + "<p>Une nouvelle connexion à votre compte CIVICA vient d'être effectuée :</p>"
                + "<div style='background-color: #f8fafc; border-left: 4px solid #16a34a; padding: 16px; border-radius: 8px; margin: 20px 0;'>"
                + "  <p style='margin: 0 0 6px 0;'><strong>📅 Date et heure :</strong> " + dateHeure + "</p>"
                + "  <p style='margin: 0 0 6px 0;'><strong>🌐 Adresse IP :</strong> " + (ipAddress != null ? ipAddress : "Non renseignée") + "</p>"
                + "  <p style='margin: 0;'><strong>📱 Navigateur / Appareil :</strong> " + (userAgent != null ? userAgent : "Non renseigné") + "</p>"
                + "</div>"
                + "<p>Si vous êtes bien à l'origine de cette connexion, aucune action n'est requise de votre part.</p>"
                + "<p style='color: #dc2626; font-size: 13px;'><strong>⚠️ Vous ne reconnaissez pas cette activité ?</strong> Veuillez modifier immédiatement votre mot de passe pour sécuriser votre compte.</p>";

        sendHtmlEmail(toEmail, subject, "Alerte de connexion à votre compte", content, null, null);
    }

    /**
     * 3. Confirmation immédiate du dépôt d'un dossier au citoyen.
     */
    @Async
    public void sendConfirmationDepot(Dossier dossier) {
        if (dossier == null || dossier.getCitoyen() == null || dossier.getCitoyen().getEmail() == null) return;

        String toEmail = dossier.getCitoyen().getEmail();
        String citoyenNom = dossier.getCitoyen().getPrenom() + " " + dossier.getCitoyen().getNom();
        String numeroSuivi = dossier.getNumeroSuivi();
        String demarcheTitre = dossier.getDemarche() != null ? dossier.getDemarche().getTitre() : "Démarche administrative";
        String lienSuivi = frontendUrl + "/suivi?numero=" + numeroSuivi;

        String subject = "CIVICA — Confirmation de dépôt de votre dossier [" + numeroSuivi + "]";

        String content = "<p>Bonjour <strong>" + citoyenNom + "</strong>,</p>"
                + "<p>Nous vous confirmons la bonne réception de votre dossier pour la démarche :</p>"
                + "<div style='background-color: #f1f5f9; padding: 16px; border-left: 4px solid #1e3a8a; border-radius: 8px; margin: 20px 0;'>"
                + "  <p style='margin: 0; font-size: 16px; font-weight: bold; color: #0f172a;'>" + demarcheTitre + "</p>"
                + "  <p style='margin: 8px 0 0 0; font-size: 14px; color: #475569;'>Numéro de suivi unique : <strong style='color: #1e3a8a; font-size: 16px;'>" + numeroSuivi + "</strong></p>"
                + "</div>"
                + "<p>Votre dossier est actuellement au statut <strong>SOUMIS</strong> et est en attente d'instruction par le service compétent.</p>"
                + "<p>Vous pouvez suivre son avancement en temps réel sans authentification en cliquant ci-dessous :</p>";

        sendHtmlEmail(toEmail, subject, "Votre dossier a été soumis avec succès.", content, "Suivre mon dossier en ligne", lienSuivi);
    }

    /**
     * 4. Notification automatique de changement de statut.
     */
    @Async
    public void sendNotificationChangementStatut(Dossier dossier, String commentaire) {
        if (dossier == null || dossier.getCitoyen() == null || dossier.getCitoyen().getEmail() == null) return;

        String toEmail = dossier.getCitoyen().getEmail();
        String citoyenNom = dossier.getCitoyen().getPrenom() + " " + dossier.getCitoyen().getNom();
        String numeroSuivi = dossier.getNumeroSuivi();
        String statut = dossier.getStatut().name();
        String lienSuivi = frontendUrl + "/suivi?numero=" + numeroSuivi;

        String statusBadgeColor;
        String statusLabel;

        switch (dossier.getStatut()) {
            case EN_VERIFICATION:
                statusBadgeColor = "#0284c7";
                statusLabel = "EN COURS D'INSTRUCTION";
                break;
            case TRANSFERE:
                statusBadgeColor = "#f59e0b";
                statusLabel = "TRANSFÉRÉ À UN AUTRE SERVICE";
                break;
            case VALIDE:
                statusBadgeColor = "#16a34a";
                statusLabel = "VALIDÉ AVEC SUCCÈS";
                break;
            case REJETE:
                statusBadgeColor = "#dc2626";
                statusLabel = "REJETÉ";
                break;
            default:
                statusBadgeColor = "#64748b";
                statusLabel = statut;
        }

        String subject = "CIVICA — Mise à jour de votre dossier [" + numeroSuivi + "] : " + statusLabel;

        StringBuilder content = new StringBuilder();
        content.append("<p>Bonjour <strong>").append(citoyenNom).append("</strong>,</p>");
        content.append("<p>Le statut de votre dossier N° <strong>").append(numeroSuivi).append("</strong> a été mis à jour :</p>");
        content.append("<div style='text-align: center; margin: 24px 0;'>")
                .append("<span style='background-color: ").append(statusBadgeColor)
                .append("; color: #ffffff; padding: 10px 20px; border-radius: 9999px; font-weight: bold; font-size: 14px;'>")
                .append(statusLabel).append("</span></div>");

        if (dossier.getStatut().name().equals("REJETE") && dossier.getMotifRejet() != null) {
            content.append("<div style='background-color: #fef2f2; border-left: 4px solid #dc2626; padding: 16px; border-radius: 8px; margin: 20px 0;'>")
                    .append("<p style='margin: 0; font-weight: bold; color: #991b1b;'>Motif du rejet :</p>")
                    .append("<p style='margin: 8px 0 0 0; color: #7f1d1d;'>").append(dossier.getMotifRejet()).append("</p>")
                    .append("</div>");
        }

        if (commentaire != null && !commentaire.isBlank() && !commentaire.equals(dossier.getMotifRejet())) {
            content.append("<p><strong>Observations de l'administration :</strong> ").append(commentaire).append("</p>");
        }

        content.append("<p>Pour consulter l'historique complet et les éventuelles pièces réclamées :</p>");

        sendHtmlEmail(toEmail, subject, "Mise à jour de statut de votre démarche.", content.toString(), "Consulter mon dossier", lienSuivi);
    }

    private void sendHtmlEmail(String to, String subject, String preheader, String contentHtml, String ctaText, String ctaUrl) {
        if (mailSender == null) {
            log.warn("[EMAIL SERVICE] JavaMailSender n'est pas initialisé. Email non envoyé à {}", to);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "Plateforme CIVICA");
            helper.setTo(to);
            helper.setSubject(subject);

            String htmlBody = buildHtmlTemplate(subject, preheader, contentHtml, ctaText, ctaUrl);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("[EMAIL SERVICE] Email envoyé avec succès à {} : {}", to, subject);
        } catch (Exception e) {
            log.error("[EMAIL SERVICE] Erreur lors de l'envoi de l'email à {} : {}", to, e.getMessage());
        }
    }

    private String buildHtmlTemplate(String title, String preheader, String contentHtml, String ctaText, String ctaUrl) {
        String buttonHtml = "";
        if (ctaText != null && ctaUrl != null) {
            buttonHtml = "<div style='text-align: center; margin: 32px 0;'>"
                    + "<a href='" + ctaUrl
                    + "' style='background-color: #1e3a8a; color: #ffffff; text-decoration: none; padding: 14px 28px; border-radius: 8px; font-weight: bold; font-size: 14px; display: inline-block; box-shadow: 0 4px 6px -1px rgba(30, 58, 138, 0.3);'>"
                    + ctaText + "</a>"
                    + "</div>";
        }

        return "<!DOCTYPE html>"
                + "<html lang='fr'>"
                + "<head><meta charset='UTF-8'><title>" + title + "</title></head>"
                + "<body style='margin: 0; padding: 0; background-color: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif; color: #1e293b;'>"
                + "<div style='display: none; max-height: 0px; overflow: hidden;'>" + preheader + "</div>"
                + "<table role='presentation' width='100%' cellspacing='0' cellpadding='0' style='background-color: #f8fafc; padding: 30px 10px;'>"
                + "<tr><td align='center'>"
                + "<table role='presentation' width='100%' style='max-width: 600px; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.05);'>"
                + "  <!-- Header -->"
                + "  <tr><td style='background: linear-gradient(135deg, #1e3a8a 0%, #0f172a 100%); padding: 30px 40px; text-align: center; border-bottom: 4px solid #16a34a;'>"
                + "    <h1 style='margin: 0; color: #ffffff; font-size: 28px; font-weight: 900; letter-spacing: 1px;'>CIVI<span style='color: #16a34a;'>CA</span></h1>"
                + "    <p style='margin: 6px 0 0 0; color: #cbd5e1; font-size: 12px; text-transform: uppercase; letter-spacing: 2px;'>Plateforme des Démarches Administratives du Cameroun</p>"
                + "  </td></tr>"
                + "  <!-- Corps -->"
                + "  <tr><td style='padding: 36px 40px; text-align: left; font-size: 15px; line-height: 1.6; color: #334155;'>"
                + contentHtml
                + buttonHtml
                + "  </td></tr>"
                + "  <!-- Footer -->"
                + "  <tr><td style='background-color: #f1f5f9; padding: 20px 40px; text-align: center; border-top: 1px solid #e2e8f0; font-size: 12px; color: #64748b;'>"
                + "    <p style='margin: 0 0 6px 0; font-weight: bold;'>CIVICA — République du Cameroun</p>"
                + "    <p style='margin: 0; font-size: 11px; color: #94a3b8;'>Ce message est généré automatiquement. Merci de ne pas y répondre directement.</p>"
                + "  </td></tr>"
                + "</table>"
                + "</td></tr>"
                + "</table>"
                + "</body></html>";
    }
}
