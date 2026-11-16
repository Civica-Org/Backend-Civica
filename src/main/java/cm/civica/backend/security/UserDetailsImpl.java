package cm.civica.backend.security;

import cm.civica.backend.model.Organisation;
import cm.civica.backend.model.Utilisateur;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
public class UserDetailsImpl implements UserDetails {
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final String nom;
    private final String prenom;
    private final String telephone;
    private final String role;
    private final Long serviceId;
    private final String serviceNom;
    private final Long organisationId;
    private final String organisationNom;
    private final boolean emailVerifie;

    @JsonIgnore
    private final String password;

    private final Collection<? extends GrantedAuthority> authorities;

    public UserDetailsImpl(Long id, String email, String nom, String prenom, String telephone,
                           String role, Long serviceId, String serviceNom,
                           Long organisationId, String organisationNom,
                           boolean emailVerifie, String password,
                           Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.nom = nom;
        this.prenom = prenom;
        this.telephone = telephone;
        this.role = role;
        this.serviceId = serviceId;
        this.serviceNom = serviceNom;
        this.organisationId = organisationId;
        this.organisationNom = organisationNom;
        this.emailVerifie = emailVerifie;
        this.password = password;
        this.authorities = authorities;
    }

    public static UserDetailsImpl build(Utilisateur utilisateur) {
        GrantedAuthority authority = new SimpleGrantedAuthority(utilisateur.getRole().name());
        Long sId = utilisateur.getService() != null ? utilisateur.getService().getId() : null;
        String sNom = utilisateur.getService() != null ? utilisateur.getService().getNom() : null;
        Organisation org = utilisateur.resolveEffectiveOrganisation();
        Long oId = org != null ? org.getId() : null;
        String oNom = org != null ? org.getNom() : null;

        return new UserDetailsImpl(
                utilisateur.getId(),
                utilisateur.getEmail(),
                utilisateur.getNom(),
                utilisateur.getPrenom(),
                utilisateur.getTelephone(),
                utilisateur.getRole().name(),
                sId,
                sNom,
                oId,
                oNom,
                Boolean.TRUE.equals(utilisateur.getEmailVerifie()),
                utilisateur.getMotDePasseHash(),
                Collections.singletonList(authority)
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

