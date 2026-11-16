package cm.civica.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Transaction de paiement simulé des droits légaux afférents à un dossier (Livrable 14 — Processus P6).
 */
@Entity
@Table(name = "paiements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Paiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_transaction", nullable = false, unique = true, length = 80)
    private String referenceTransaction;

    @Column(name = "quittance_numero", unique = true, length = 80)
    private String quittanceNumero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id")
    @JsonIgnoreProperties({"pieces", "historiques", "hibernateLazyInitializer", "handler"})
    private Dossier dossier;

    @Column(name = "demarche_code", length = 80)
    private String demarcheCode;

    @Column(name = "service_id")
    private Long serviceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citoyen_id", nullable = false)
    @JsonIgnoreProperties({"motDePasseHash", "hibernateLazyInitializer", "handler"})
    private Utilisateur citoyen;

    @Column(name = "montant_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantTotal;

    @Builder.Default
    @Column(name = "devise", nullable = false, length = 10)
    private String devise = "XAF";

    @Column(name = "mode_paiement", nullable = false, length = 50)
    private String modePaiement; // MOMO, OM, CARTE_BANCAIRE, COMPTE_BANCAIRE

    @Column(name = "instrument_masque", length = 80)
    private String instrumentMasque;

    @Column(name = "variante_circuit", length = 80)
    private String varianteCircuit;

    @Builder.Default
    @Column(name = "statut", nullable = false, length = 30)
    private String statut = "CONFIRME"; // EN_ATTENTE, CONFIRME, ECHOUE

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @JsonManagedReference
    @Builder.Default
    @OneToMany(mappedBy = "paiement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentAllocation> allocations = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = Instant.now();
        if (this.devise == null) this.devise = "XAF";
        if (this.statut == null) this.statut = "CONFIRME";
    }

    public void ajouterAllocation(PaymentAllocation allocation) {
        this.allocations.add(allocation);
        allocation.setPaiement(this);
    }
}

