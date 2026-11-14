package cm.civica.backend.repository;

import cm.civica.backend.model.HistoriqueDossier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoriqueDossierRepository extends JpaRepository<HistoriqueDossier, Long> {
    List<HistoriqueDossier> findByDossierIdOrderByTimestampDesc(Long dossierId);
    List<HistoriqueDossier> findByDossierNumeroSuiviOrderByTimestampDesc(String numeroSuivi);
}
