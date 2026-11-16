package cm.civica.backend.repository;

import cm.civica.backend.model.WithdrawalRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {
    List<WithdrawalRequest> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);

    List<WithdrawalRequest> findAllByOrderByCreatedAtDesc();

    long countByStatut(String statut);

    long countByOrganisationIdAndStatut(Long organisationId, String statut);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT wr FROM WithdrawalRequest wr JOIN FETCH wr.wallet JOIN FETCH wr.organisation JOIN FETCH wr.requestedBy WHERE wr.id = :id")
    Optional<WithdrawalRequest> findByIdForUpdate(@Param("id") Long id);
}
