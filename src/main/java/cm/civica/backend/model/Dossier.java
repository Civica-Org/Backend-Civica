package cm.civica.backend.model;

import cm.civica.backend.enums.StatutDossier;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "dossiers",
    indexes = {
        @Index(name = "idx_dossiers_numero_suivi", columnList = "numero_suivi"),
        @Index(name = "idx_dossiers_citoyen_id", columnList = "citoyen_id"),
        @Index(name = "idx_dossiers_service_courant", columnList = "service_courant_id"),
        @Index(name = "idx_dossiers_statut", columnList = "statut")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Dossier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_suivi", nullable = false, unique = true, length = 64)
    private String numeroSuivi;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citoyen_id", nullable = false)
    @JsonIgnoreProperties({"motDePasseHash", "hibernateLazyInitializer", "handler"})
    private Utilisateur citoyen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demarche_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Demarche demarche;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_courant_id")
    @JsonIgnoreProperties({"organisation", "hibernateLazyInitializer", "handler"})
    private ServiceAdministratif serviceCourant;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StatutDossier statut = StatutDossier.SOUMIS;

    @Builder.Default
    @Column(length = 20)
    private String priorite = "NORMALE";

    @Column(name = "motif_rejet", columnDefinition = "TEXT")
    private String motifRejet;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @JsonManagedReference
    @Builder.Default
    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PieceJustificative> pieces = new ArrayList<>();

    @JsonManagedReference
    @Builder.Default
    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HistoriqueDossier> historiques = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
        if (this.statut == null) {
            this.statut = StatutDossier.SOUMIS;
        }
        if (this.priorite == null) {
            this.priorite = "NORMALE";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void ajouterPiece(PieceJustificative piece) {
        this.pieces.add(piece);
        piece.setDossier(this);
    }

    public void ajouterHistorique(HistoriqueDossier historique) {
        this.historiques.add(historique);
        historique.setDossier(this);
    }

    public void transitionner(StatutDossier nouvelEtat) {
        this.statut = nouvelEtat;
        this.updatedAt = Instant.now();
    }

    public void transferer(ServiceAdministratif cible) {
        this.serviceCourant = cible;
        this.statut = StatutDossier.TRANSFERE;
        this.updatedAt = Instant.now();
    }
}


