package cm.civica.backend.repository;

import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.model.Dossier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DossierRepository extends JpaRepository<Dossier, Long> {
    Optional<Dossier> findByNumeroSuivi(String numeroSuivi);
    boolean existsByNumeroSuivi(String numeroSuivi);

    List<Dossier> findByCitoyenIdOrderByCreatedAtDesc(Long citoyenId);
    List<Dossier> findByServiceCourantId(Long serviceId);
    List<Dossier> findByServiceCourantIdAndStatut(Long serviceId, StatutDossier statut);
    List<Dossier> findByServiceCourantOrganisationId(Long organisationId);
    List<Dossier> findByServiceCourantOrganisationIdAndStatut(Long organisationId, StatutDossier statut);
    List<Dossier> findByStatut(StatutDossier statut);

    @Query("SELECT d FROM Dossier d LEFT JOIN FETCH d.pieces LEFT JOIN FETCH d.demarche WHERE d.numeroSuivi = :numeroSuivi")
    Optional<Dossier> findByNumeroSuiviWithPiecesAndDemarche(@Param("numeroSuivi") String numeroSuivi);

    long countByStatut(StatutDossier statut);

    long countByServiceCourantId(Long serviceId);

    long countByDemarcheId(Long demarcheId);

    long countByServiceCourantIdAndStatut(Long serviceId, StatutDossier statut);

    long countByNumeroSuiviStartingWith(String prefix);
}
