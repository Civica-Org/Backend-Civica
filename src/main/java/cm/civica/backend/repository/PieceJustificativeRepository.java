package cm.civica.backend.repository;

import cm.civica.backend.model.PieceJustificative;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PieceJustificativeRepository extends JpaRepository<PieceJustificative, Long> {
    List<PieceJustificative> findByDossierId(Long dossierId);
    List<PieceJustificative> findByDossierNumeroSuivi(String numeroSuivi);
}
