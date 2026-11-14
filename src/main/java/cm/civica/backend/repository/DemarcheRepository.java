package cm.civica.backend.repository;

import cm.civica.backend.model.Demarche;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DemarcheRepository extends JpaRepository<Demarche, Long> {
    Optional<Demarche> findByCode(String code);
    List<Demarche> findByActifTrue();
}
