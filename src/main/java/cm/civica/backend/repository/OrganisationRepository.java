package cm.civica.backend.repository;

import cm.civica.backend.model.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganisationRepository extends JpaRepository<Organisation, Long> {
    Optional<Organisation> findByCode(String code);
    List<Organisation> findByActifTrue();
}
