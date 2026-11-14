package cm.civica.backend.controller;

import cm.civica.backend.model.Organisation;
import cm.civica.backend.model.ServiceAdministratif;
import cm.civica.backend.repository.OrganisationRepository;
import cm.civica.backend.repository.ServiceAdministratifRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organisations")
@Tag(name = "5. Organisations & Services", description = "Consultation des administrations publiques et de leurs services d'accueil")
public class OrganisationController {

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private ServiceAdministratifRepository serviceRepository;

    @Operation(summary = "Lister les organisations actives")
    @SecurityRequirements
    @GetMapping
    public ResponseEntity<List<Organisation>> getOrganisations() {
        return ResponseEntity.ok(organisationRepository.findByActifTrue());
    }

    @Operation(summary = "Lister les services d'une organisation")
    @SecurityRequirements
    @GetMapping("/{organisationId}/services")
    public ResponseEntity<List<ServiceAdministratif>> getServicesByOrganisation(@PathVariable Long organisationId) {
        return ResponseEntity.ok(serviceRepository.findByOrganisationId(organisationId));
    }
}

