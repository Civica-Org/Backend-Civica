package cm.civica.backend.repository;

import cm.civica.backend.model.InstitutionWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InstitutionWalletRepository extends JpaRepository<InstitutionWallet, Long> {
    Optional<InstitutionWallet> findByOrganisationId(Long organisationId);

    Optional<InstitutionWallet> findByOrganisationCode(String organisationCode);

    List<InstitutionWallet> findAllByOrderByOrganisationNomAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM InstitutionWallet w JOIN FETCH w.organisation WHERE w.id = :id")
    Optional<InstitutionWallet> findByIdForUpdate(@Param("id") Long id);
}
