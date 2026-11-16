package cm.civica.backend.model;

import cm.civica.backend.enums.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "utilisateurs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @JsonIgnore
    @Column(name = "mot_de_passe_hash", nullable = false, length = 255)
    private String motDePasseHash;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 100)
    private String prenom;

    @Column(length = 50)
    private String telephone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    @JsonIgnoreProperties({"organisation", "hibernateLazyInitializer", "handler"})
    private ServiceAdministratif service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id")
    @JsonIgnoreProperties({"services", "hibernateLazyInitializer", "handler"})
    private Organisation organisation;

    public Organisation resolveEffectiveOrganisation() {
        if (this.organisation != null) {
            return this.organisation;
        }
        return this.service != null ? this.service.getOrganisation() : null;
    }

    @Builder.Default
    @Column(name = "email_verifie", nullable = false)
    private Boolean emailVerifie = false;

    @JsonIgnore
    @Column(name = "code_verification", length = 10)
    private String codeVerification;

    @JsonIgnore
    @Column(name = "code_verification_expiration")
    private Instant codeVerificationExpiration;

    @Builder.Default
    @Column(nullable = false)
    private Boolean actif = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.actif == null) {
            this.actif = true;
        }
        if (this.emailVerifie == null) {
            this.emailVerifie = false;
        }
    }

    public boolean isEmailVerifie() {
        return Boolean.TRUE.equals(this.emailVerifie);
    }
}

