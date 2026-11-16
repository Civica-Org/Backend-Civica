package cm.civica.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Demande de décaissement / retrait financier depuis un portefeuille institutionnel
 * soumise au principe strict des quatre yeux (Livrable 15 — Processus P7).
 * Invariant de sécurité : authorizedBy != requestedBy.
 */
@Entity
@Table(name = "withdrawal_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class WithdrawalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_demande", nullable = false, unique = true, length = 80)
    private String referenceDemande;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private InstitutionWallet wallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    @JsonIgnoreProperties({"services", "hibernateLazyInitializer", "handler"})
    private Organisation organisation;

    @Column(name = "montant", nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(name = "compte_beneficiaire", nullable = false, length = 150)
    private String compteBeneficiaire;

    @Column(name = "motif", nullable = false, length = 500)
    private String motif;

    @Builder.Default
    @Column(name = "statut", nullable = false, length = 30)
    private String statut = "DEMANDE"; // DEMANDE, EXECUTE, REFUSE

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_id", nullable = false)
    @JsonIgnoreProperties({"motDePasseHash", "hibernateLazyInitializer", "handler"})
    private Utilisateur requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "authorized_by_id")
    @JsonIgnoreProperties({"motDePasseHash", "hibernateLazyInitializer", "handler"})
    private Utilisateur authorizedBy;

    @Column(name = "commentaire_decision", length = 500)
    private String commentaireDecision;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = Instant.now();
        if (this.statut == null) this.statut = "DEMANDE";
    }
}

