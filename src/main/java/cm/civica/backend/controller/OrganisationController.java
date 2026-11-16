package cm.civica.backend.controller;

import cm.civica.backend.dto.ServiceAdminDto;
import cm.civica.backend.model.Organisation;
import cm.civica.backend.repository.OrganisationRepository;
import cm.civica.backend.repository.ServiceAdministratifRepository;
import cm.civica.backend.service.PersonnelHierarchyHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/organisations")
@Tag(name = "5. Organisations & Services", description = "Consultation des administrations publiques et de leurs services d'accueil")
public class OrganisationController {

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private ServiceAdministratifRepository serviceRepository;

    @Autowired
    private PersonnelHierarchyHelper personnelHierarchyHelper;

    @Operation(summary = "Lister les organisations actives")
    @SecurityRequirements
    @Transactional(readOnly = true)
    @GetMapping
    public ResponseEntity<List<Organisation>> getOrganisations() {
        return ResponseEntity.ok(organisationRepository.findByActifTrue());
    }

    @Operation(summary = "Lister tous les services administratifs actifs")
    @SecurityRequirements
    @Transactional(readOnly = true)
    @GetMapping("/services")
    public ResponseEntity<List<ServiceAdminDto>> getAllActiveServices() {
        List<ServiceAdminDto> dtos = serviceRepository.findByActifTrue().stream()
                .map(personnelHierarchyHelper::toServiceAdminDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @Operation(summary = "Lister les services actifs d'une organisation")
    @SecurityRequirements
    @Transactional(readOnly = true)
    @GetMapping("/{organisationId}/services")
    public ResponseEntity<List<ServiceAdminDto>> getServicesByOrganisation(@PathVariable Long organisationId) {
        List<ServiceAdminDto> dtos = serviceRepository.findByOrganisationIdAndActifTrue(organisationId).stream()
                .map(personnelHierarchyHelper::toServiceAdminDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
}

