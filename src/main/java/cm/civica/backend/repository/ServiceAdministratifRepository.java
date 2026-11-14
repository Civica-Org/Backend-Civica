package cm.civica.backend.repository;

import cm.civica.backend.model.ServiceAdministratif;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceAdministratifRepository extends JpaRepository<ServiceAdministratif, Long> {
    List<ServiceAdministratif> findByOrganisationId(Long organisationId);
    Optional<ServiceAdministratif> findByOrganisationIdAndCode(Long organisationId, String code);
    List<ServiceAdministratif> findByActifTrue();
}
