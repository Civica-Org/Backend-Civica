package cm.civica.backend.repository;

import cm.civica.backend.model.Paiement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaiementRepository extends JpaRepository<Paiement, Long> {
    List<Paiement> findByDossierNumeroSuiviOrderByCreatedAtDesc(String numeroSuivi);

    Optional<Paiement> findFirstByDossierIdAndStatutOrderByCreatedAtDesc(Long dossierId, String statut);

    Optional<Paiement> findFirstByDossierIdOrderByCreatedAtDesc(Long dossierId);

    boolean existsByDossierIdAndStatut(Long dossierId, String statut);

    Optional<Paiement> findByReferenceTransaction(String referenceTransaction);

    List<Paiement> findByCitoyenIdOrderByCreatedAtDesc(Long citoyenId);

    List<Paiement> findByCitoyenIdAndDossierIsNullAndStatutOrderByCreatedAtDesc(Long citoyenId, String statut);

    List<Paiement> findByCitoyenIdAndDemarcheCodeAndDossierIsNullAndStatutOrderByCreatedAtDesc(
            Long citoyenId, String demarcheCode, String statut);

    List<Paiement> findTop50ByOrderByCreatedAtDesc();
}
