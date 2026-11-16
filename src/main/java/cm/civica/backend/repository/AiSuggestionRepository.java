package cm.civica.backend.repository;

import cm.civica.backend.model.AiSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, Long> {

    List<AiSuggestion> findTop50ByOrderByCreatedAtDesc();

    List<AiSuggestion> findByDossierIdOrderByCreatedAtDesc(Long dossierId);

    List<AiSuggestion> findTop30ByDemandeurIdOrderByCreatedAtDesc(Long demandeurId);
}

