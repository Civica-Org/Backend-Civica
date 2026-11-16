package cm.civica.backend.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Quote-part de répartition financière d'un paiement citoyen vers le portefeuille
 * interne d'une organisation administrative (Livrable 14 — PaymentAllocation).
 */
@Entity
@Table(name = "payment_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paiement_id", nullable = false)
    private Paiement paiement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private InstitutionWallet wallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    @JsonIgnoreProperties({"services", "hibernateLazyInitializer", "handler"})
    private Organisation organisation;

    @Column(name = "libelle_quote_part", nullable = false, length = 255)
    private String libelleQuotePart;

    @Column(name = "pourcentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal pourcentage;

    @Column(name = "montant_alloue", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantAlloue;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}

