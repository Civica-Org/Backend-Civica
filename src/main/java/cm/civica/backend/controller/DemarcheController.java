package cm.civica.backend.controller;

import cm.civica.backend.dto.DemarcheDto;
import cm.civica.backend.service.DemarcheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/demarches")
@Tag(name = "2. Catalogue des Démarches", description = "Consultation publique des démarches administratives supportées par CIVICA")
public class DemarcheController {

    @Autowired
    private DemarcheService demarcheService;

    @Operation(summary = "Lister les démarches actives", description = "Retourne la liste des démarches administratives disponibles (CNI, Acte de naissance, Casier judiciaire...).")
    @SecurityRequirements
    @GetMapping
    public ResponseEntity<List<DemarcheDto>> getAllDemarches() {
        return ResponseEntity.ok(demarcheService.getAllDemarchesActives());
    }

    @Operation(summary = "Consulter une démarche par son code", description = "Retourne le détail d'une démarche administrative via son code (ex: DEM-CNI).")
    @SecurityRequirements
    @GetMapping("/{code}")
    public ResponseEntity<DemarcheDto> getDemarcheByCode(@PathVariable String code) {
        return ResponseEntity.ok(demarcheService.getDemarcheByCode(code));
    }
}

