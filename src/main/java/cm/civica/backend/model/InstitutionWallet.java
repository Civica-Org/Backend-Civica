package cm.civica.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Portefeuille financier interne d'une organisation administrative (Livrable 14 & 15).
 * Centralise les fonds perçus par répartition automatique (PaymentAllocation) et
 * gère les réserves bloquées lors des demandes de retrait à quatre yeux (WithdrawalRequest).
 */
@Entity
@Table(name = "institution_wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class InstitutionWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false, unique = true)
    @JsonIgnoreProperties({"services", "hibernateLazyInitializer", "handler"})
    private Organisation organisation;

    @Column(name = "numero_compte_tresor", nullable = false, unique = true, length = 100)
    private String numeroCompteTresor;

    @Builder.Default
    @Column(name = "solde_disponible", nullable = false, precision = 15, scale = 2)
    private BigDecimal soldeDisponible = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "solde_bloque", nullable = false, precision = 15, scale = 2)
    private BigDecimal soldeBloque = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_encaisse", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalEncaisse = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_decaisse", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDecaisse = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "devise", nullable = false, length = 10)
    private String devise = "XAF";

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    @PreUpdate
    public void touch() {
        this.updatedAt = Instant.now();
        if (this.soldeDisponible == null) this.soldeDisponible = BigDecimal.ZERO;
        if (this.soldeBloque == null) this.soldeBloque = BigDecimal.ZERO;
        if (this.totalEncaisse == null) this.totalEncaisse = BigDecimal.ZERO;
        if (this.totalDecaisse == null) this.totalDecaisse = BigDecimal.ZERO;
        if (this.devise == null) this.devise = "XAF";
    }

    public void crediterAllocation(BigDecimal montant) {
        this.soldeDisponible = this.soldeDisponible.add(montant);
        this.totalEncaisse = this.totalEncaisse.add(montant);
        this.updatedAt = Instant.now();
    }

    public void bloquerPourRetrait(BigDecimal montant) {
        this.soldeDisponible = this.soldeDisponible.subtract(montant);
        this.soldeBloque = this.soldeBloque.add(montant);
        this.updatedAt = Instant.now();
    }

    public void executerRetraitBloque(BigDecimal montant) {
        this.soldeBloque = this.soldeBloque.subtract(montant);
        this.totalDecaisse = this.totalDecaisse.add(montant);
        this.updatedAt = Instant.now();
    }

    public void debloquerSuiteRefus(BigDecimal montant) {
        this.soldeBloque = this.soldeBloque.subtract(montant);
        this.soldeDisponible = this.soldeDisponible.add(montant);
        this.updatedAt = Instant.now();
    }
}

