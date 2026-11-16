package cm.civica.backend.repository;

import cm.civica.backend.enums.Role;
import cm.civica.backend.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {
    Optional<Utilisateur> findByEmail(String email);
    Optional<Utilisateur> findByEmailIgnoreCase(String email);
    boolean existsByEmail(String email);
    Optional<Utilisateur> findByTelephone(String telephone);
    boolean existsByTelephone(String telephone);

    @Query("SELECT u FROM Utilisateur u WHERE LOWER(u.email) = LOWER(:identifier) OR u.telephone = :identifier")
    Optional<Utilisateur> findByIdentifier(@Param("identifier") String identifier);

    List<Utilisateur> findByRole(Role role);
    List<Utilisateur> findByRoleIn(Collection<Role> roles);
    List<Utilisateur> findByServiceId(Long serviceId);
    List<Utilisateur> findByServiceIdAndRole(Long serviceId, Role role);
    List<Utilisateur> findByOrganisationId(Long organisationId);
    List<Utilisateur> findByOrganisationIdAndRole(Long organisationId, Role role);
    List<Utilisateur> findByServiceOrganisationId(Long organisationId);
    List<Utilisateur> findByServiceOrganisationIdAndRole(Long organisationId, Role role);
}
