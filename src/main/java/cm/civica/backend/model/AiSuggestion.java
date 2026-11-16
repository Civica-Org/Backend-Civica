package cm.civica.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Trace immuable d'assistance par intelligence artificielle (Chapitre 8 : table ai_suggestions).
 * Permet d'auditer quelle suggestion provient d'un LLM (ou du repli déterministe)
 * et quel agent humain a validé ou rejeté la décision finale.
 */
@Entity
@Table(
    name = "ai_suggestions",
    indexes = {
        @Index(name = "idx_ai_suggestions_dossier_id", columnList = "dossier_id"),
        @Index(name = "idx_ai_suggestions_type", columnList = "type_assistance"),
        @Index(name = "idx_ai_suggestions_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AiSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id")
    private Dossier dossier;

    @Column(name = "type_assistance", nullable = false, length = 50)
    private String typeAssistance;

    @Column(nullable = false, length = 60)
    private String fournisseur;

    @Column(length = 80)
    private String modele;

    @Column(name = "resume_prompt_anonymise", columnDefinition = "TEXT")
    private String resumePromptAnonymise;

    @Column(name = "reponse_json", columnDefinition = "TEXT")
    private String reponseJson;

    @Builder.Default
    @Column(name = "suggestion_issu_ia", nullable = false)
    private boolean suggestionIssuIa = true;

    @Builder.Default
    @Column(name = "mode_degrade", nullable = false)
    private boolean modeDegrade = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demandeur_id")
    @JsonIgnoreProperties({"motDePasseHash", "hibernateLazyInitializer", "handler"})
    private Utilisateur demandeur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validateur_id")
    @JsonIgnoreProperties({"motDePasseHash", "hibernateLazyInitializer", "handler"})
    private Utilisateur validateur;

    @Column(name = "acceptee_par_agent")
    private Boolean accepteeParAgent;

    @Column(name = "latence_ms")
    private Long latenceMs;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}

