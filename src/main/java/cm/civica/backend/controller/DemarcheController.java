package cm.civica.backend.controller;

import cm.civica.backend.compiler.ConditionCompilerService;
import cm.civica.backend.dto.DemarcheDto;
import cm.civica.backend.service.DemarcheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/demarches")
@Tag(name = "2. Catalogue des Démarches", description = "Consultation publique des démarches administratives et compilateur EBNF de sélection de circuit")
public class DemarcheController {

    @Autowired
    private DemarcheService demarcheService;

    @Autowired
    private ConditionCompilerService conditionCompilerService;

    @Operation(summary = "Lister les démarches actives", description = "Retourne la liste des démarches administratives disponibles (CNI, Acte de naissance, Casier judiciaire...).")
    @SecurityRequirements
    @GetMapping
    public ResponseEntity<List<DemarcheDto>> getAllDemarches() {
        return ResponseEntity.ok(demarcheService.getAllDemarchesActives());
    }

    @Operation(summary = "Évaluer le circuit d'Acte de Naissance via le compilateur EBNF (Livrables 30 & 31)",
            description = "Compile et évalue les règles EBNF des 3 circuits (NORMALE <= 90j, TARDIVE 91-365j, JUDICIAIRE > 365j) et retourne le workflow applicable.")
    @SecurityRequirements
    @GetMapping("/evaluer-circuit")
    public ResponseEntity<Map<String, Object>> evaluerCircuitActeNaissance(
            @RequestParam(defaultValue = "45") int joursDepuisNaissance,
            @RequestParam(defaultValue = "true") boolean naissanceCameroun) {
        Map<String, Object> contexte = Map.of(
                "jours_depuis_naissance", joursDepuisNaissance,
                "naissance_cameroun", naissanceCameroun
        );
        return ResponseEntity.ok(conditionCompilerService.determinerCircuitActeNaissance(contexte));
    }

    @Operation(summary = "Consulter une démarche par son code", description = "Retourne le détail d'une démarche administrative via son code (ex: DEM-CNI).")
    @SecurityRequirements
    @GetMapping("/{code}")
    public ResponseEntity<DemarcheDto> getDemarcheByCode(@PathVariable String code) {
        return ResponseEntity.ok(demarcheService.getDemarcheByCode(code));
    }
}

