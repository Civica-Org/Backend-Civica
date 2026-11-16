package cm.civica.backend.repository;

import cm.civica.backend.model.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {
    List<PaymentAllocation> findTop30ByWalletIdOrderByCreatedAtDesc(Long walletId);

    List<PaymentAllocation> findTop50ByOrderByCreatedAtDesc();
}
