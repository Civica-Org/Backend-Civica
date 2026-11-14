package cm.civica.backend.model;

import cm.civica.backend.enums.StatutPiece;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "pieces_justificatives",
    indexes = {
        @Index(name = "idx_pieces_dossier_id", columnList = "dossier_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PieceJustificative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @Column(nullable = false, length = 255)
    private String intitule;

    @Column(name = "type_mime", nullable = false, length = 100)
    private String typeMime;

    @Column(name = "nom_fichier_stocke", nullable = false, length = 255)
    private String nomFichierStocke;

    @Column(name = "taille_octets", nullable = false)
    private Long tailleOctets;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_validation", length = 50)
    private StatutPiece statutValidation = StatutPiece.EN_ATTENTE;

    @Column(name = "observations_ia", columnDefinition = "TEXT")
    private String observationsIa;

    @Builder.Default
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (this.uploadedAt == null) {
            this.uploadedAt = Instant.now();
        }
        if (this.statutValidation == null) {
            this.statutValidation = StatutPiece.EN_ATTENTE;
        }
    }
}

