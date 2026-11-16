package cm.civica.backend.repository;

import cm.civica.backend.model.OrganisationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganisationTypeRepository extends JpaRepository<OrganisationType, Long> {
    Optional<OrganisationType> findByCode(String code);
    Optional<OrganisationType> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    List<OrganisationType> findAllByOrderByLibelleAsc();
}

