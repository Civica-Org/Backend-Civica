package cm.civica.backend.model;

import cm.civica.backend.enums.StatutDossier;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "historique_dossiers",
    indexes = {
        @Index(name = "idx_historique_dossier_id", columnList = "dossier_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class HistoriqueDossier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    @JsonIgnoreProperties({"motDePasseHash", "hibernateLazyInitializer", "handler"})
    private Utilisateur agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_source_id")
    @JsonIgnoreProperties({"organisation", "hibernateLazyInitializer", "handler"})
    private ServiceAdministratif serviceSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_cible_id")
    @JsonIgnoreProperties({"organisation", "hibernateLazyInitializer", "handler"})
    private ServiceAdministratif serviceCible;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_precedent", length = 50)
    private StatutDossier statutPrecedent;

    @Enumerated(EnumType.STRING)
    @Column(name = "nouveau_statut", nullable = false, length = 50)
    private StatutDossier nouveauStatut;

    @Column(name = "action_effectuee", nullable = false, length = 100)
    private String actionEffectuee;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant timestamp = Instant.now();

    @PrePersist
    public void prePersist() {
        if (this.timestamp == null) {
            this.timestamp = Instant.now();
        }
    }
}

