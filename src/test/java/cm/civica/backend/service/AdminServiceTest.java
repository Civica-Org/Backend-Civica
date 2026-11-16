package cm.civica.backend.service;

import cm.civica.backend.dto.CreateOrganisationRequest;
import cm.civica.backend.dto.CreateOrganisationTypeRequest;
import cm.civica.backend.dto.UpdateOrganisationTypeRequest;
import cm.civica.backend.dto.UtilisateurAdminDto;
import cm.civica.backend.enums.Role;
import cm.civica.backend.exception.BusinessException;
import cm.civica.backend.model.Organisation;
import cm.civica.backend.model.OrganisationType;
import cm.civica.backend.model.Utilisateur;
import cm.civica.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private OrganisationRepository organisationRepository;

    @Mock
    private OrganisationTypeRepository organisationTypeRepository;

    @Mock
    private ServiceAdministratifRepository serviceRepository;

    @Mock
    private DemarcheRepository demarcheRepository;

    @Mock
    private DossierRepository dossierRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private FinanceService financeService;

    @Mock
    private EmailService emailService;

    @Mock
    private PersonnelHierarchyHelper personnelHierarchyHelper;

    @InjectMocks
    private AdminService adminService;

    @Test
    void lookupUserByEmailReturnsIdentityDetailsWhenFound() {
        Utilisateur citoyen = Utilisateur.builder()
                .id(42L)
                .email("jean.kamga@civica.cm")
                .nom("Kamga")
                .prenom("Jean")
                .telephone("+237670112233")
                .role(Role.ROLE_CITOYEN)
                .actif(true)
                .emailVerifie(true)
                .createdAt(Instant.parse("2026-10-01T10:00:00Z"))
                .build();

        when(utilisateurRepository.findByEmailIgnoreCase("jean.kamga@civica.cm"))
                .thenReturn(Optional.of(citoyen));

        UtilisateurAdminDto dto = adminService.lookupUserByEmail("  Jean.Kamga@Civica.CM ");

        assertEquals(42L, dto.getId());
        assertEquals("Kamga", dto.getNom());
        assertEquals("Jean", dto.getPrenom());
        assertEquals("+237670112233", dto.getTelephone());
        assertEquals("jean.kamga@civica.cm", dto.getEmail());
        assertEquals(Role.ROLE_CITOYEN, dto.getRole());
        assertTrue(Boolean.TRUE.equals(dto.getEmailVerifie()));
    }

    @Test
    void lookupUserByEmailThrowsNotFoundWhenMissing() {
        when(utilisateurRepository.findByEmailIgnoreCase("inconnu@civica.cm"))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminService.lookupUserByEmail("inconnu@civica.cm"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void createOrganisationWithValidResponsablePromotesUserAndSendsNotificationEmail() {
        Utilisateur futurResp = Utilisateur.builder()
                .id(77L)
                .email("nadia.biya@civica.cm")
                .nom("Biya")
                .prenom("Nadia")
                .telephone("+237699887766")
                .role(Role.ROLE_CITOYEN)
                .actif(true)
                .emailVerifie(true)
                .build();

        Organisation savedOrg = Organisation.builder()
                .id(15L)
                .code("MINESEC")
                .nom("Ministère des Enseignements Secondaires")
                .typeOrganisation("MINISTERE")
                .actif(true)
                .build();

        when(organisationRepository.findByCode("MINESEC")).thenReturn(Optional.empty());
        when(utilisateurRepository.findByEmailIgnoreCase("nadia.biya@civica.cm"))
                .thenReturn(Optional.of(futurResp));
        when(organisationRepository.save(any(Organisation.class))).thenReturn(savedOrg);
        when(personnelHierarchyHelper.applyUserUpdate(futurResp, Role.ROLE_RESPONSABLE, 15L, null, null))
                .thenReturn(UtilisateurAdminDto.builder().id(77L).role(Role.ROLE_RESPONSABLE).organisationId(15L).build());

        CreateOrganisationRequest req = CreateOrganisationRequest.builder()
                .code("minesec")
                .nom("Ministère des Enseignements Secondaires")
                .typeOrganisation("MINISTERE")
                .responsableEmail("Nadia.Biya@civica.cm")
                .build();

        Organisation result = adminService.createOrganisation(req);

        assertEquals(15L, result.getId());
        assertEquals("MINESEC", result.getCode());
        verify(financeService).ensureWalletForOrganisation(savedOrg);
        verify(personnelHierarchyHelper).applyUserUpdate(futurResp, Role.ROLE_RESPONSABLE, 15L, null, null);
        verify(emailService).sendOrganisationResponsableNotification(futurResp, savedOrg);
    }

    @Test
    void createOrganisationRejectsUnknownResponsableEmailWithoutSavingOrg() {
        when(organisationRepository.findByCode("MINFI")).thenReturn(Optional.empty());
        when(utilisateurRepository.findByEmailIgnoreCase("absent@civica.cm"))
                .thenReturn(Optional.empty());

        CreateOrganisationRequest req = CreateOrganisationRequest.builder()
                .code("MINFI")
                .nom("Ministère des Finances")
                .typeOrganisation("MINISTERE")
                .responsableEmail("absent@civica.cm")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> adminService.createOrganisation(req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(organisationRepository, never()).save(any());
        verify(emailService, never()).sendOrganisationResponsableNotification(any(), any());
    }

    @Test
    void createOrganisationRejectsAlreadyAssignedResponsableWithoutSavingOrg() {
        Organisation autreOrg = Organisation.builder().id(3L).code("DGSN").nom("DGSN").build();
        Utilisateur dejaResp = Utilisateur.builder()
                .id(88L)
                .email("chef@civica.cm")
                .nom("Essomba")
                .prenom("Marc")
                .role(Role.ROLE_RESPONSABLE)
                .organisation(autreOrg)
                .actif(true)
                .build();

        when(organisationRepository.findByCode("MINJUSTICE")).thenReturn(Optional.empty());
        when(utilisateurRepository.findByEmailIgnoreCase("chef@civica.cm"))
                .thenReturn(Optional.of(dejaResp));

        CreateOrganisationRequest req = CreateOrganisationRequest.builder()
                .code("MINJUSTICE")
                .nom("Ministère de la Justice")
                .typeOrganisation("MINISTERE")
                .responsableEmail("chef@civica.cm")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> adminService.createOrganisation(req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(organisationRepository, never()).save(any());
        verify(emailService, never()).sendOrganisationResponsableNotification(any(), any());
    }

    @Test
    void createOrganisationWithoutResponsableSucceedsWithoutEmail() {
        Organisation savedOrg = Organisation.builder()
                .id(20L)
                .code("MINSANTE")
                .nom("Ministère de la Santé")
                .typeOrganisation("MINISTERE")
                .actif(true)
                .build();

        when(organisationRepository.findByCode("MINSANTE")).thenReturn(Optional.empty());
        when(organisationRepository.save(any(Organisation.class))).thenReturn(savedOrg);

        CreateOrganisationRequest req = CreateOrganisationRequest.builder()
                .code("MINSANTE")
                .nom("Ministère de la Santé")
                .typeOrganisation("MINISTERE")
                .responsableEmail("   ")
                .build();

        Organisation result = adminService.createOrganisation(req);

        assertEquals(20L, result.getId());
        verify(personnelHierarchyHelper, never()).applyUserUpdate(any(), any(), any(), any(), any());
        verify(emailService, never()).sendOrganisationResponsableNotification(any(), any());
    }

    @Test
    void createOrganisationTypeGeneratesNormalizedCodeAndSaves() {
        CreateOrganisationTypeRequest req = CreateOrganisationTypeRequest.builder()
                .libelle("Agence d'État")
                .description("Établissement public autonome")
                .build();

        when(organisationTypeRepository.existsByCodeIgnoreCase("AGENCE_D_ETAT")).thenReturn(false);
        when(organisationTypeRepository.save(any(OrganisationType.class)))
                .thenAnswer(invocation -> {
                    OrganisationType t = invocation.getArgument(0);
                    t.setId(7L);
                    return t;
                });

        OrganisationType created = adminService.createOrganisationType(req);

        assertEquals(7L, created.getId());
        assertEquals("AGENCE_D_ETAT", created.getCode());
        assertEquals("Agence d'État", created.getLibelle());
        assertEquals("Établissement public autonome", created.getDescription());
        assertTrue(created.getActif());
    }

    @Test
    void createOrganisationTypePreservesExplicitCodeAndRejectsDuplicate() {
        CreateOrganisationTypeRequest explicitReq = CreateOrganisationTypeRequest.builder()
                .code("epst-etat")
                .libelle("Établissement Public")
                .build();

        when(organisationTypeRepository.existsByCodeIgnoreCase("EPST_ETAT")).thenReturn(false);
        when(organisationTypeRepository.save(any(OrganisationType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrganisationType created = adminService.createOrganisationType(explicitReq);
        assertEquals("EPST_ETAT", created.getCode());

        when(organisationTypeRepository.existsByCodeIgnoreCase("EPST_ETAT")).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminService.createOrganisationType(explicitReq));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void updateAndToggleOrganisationTypeStatusWork() {
        OrganisationType existing = OrganisationType.builder()
                .id(5L)
                .code("ORDRE_PRO")
                .libelle("Ordre Professionnel")
                .description("Ancienne description")
                .actif(true)
                .build();

        when(organisationTypeRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(organisationTypeRepository.save(any(OrganisationType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateOrganisationTypeRequest updateReq = UpdateOrganisationTypeRequest.builder()
                .libelle("Ordre Professionnel National")
                .description("  ")
                .actif(false)
                .build();

        OrganisationType updated = adminService.updateOrganisationType(5L, updateReq);
        assertEquals("Ordre Professionnel National", updated.getLibelle());
        assertNull(updated.getDescription());
        assertFalse(updated.getActif());

        OrganisationType toggled = adminService.toggleOrganisationTypeStatus(5L);
        assertTrue(toggled.getActif());
    }

    @Test
    void deleteOrganisationTypeBlocksWhenUsedAndDeletesWhenUnused() {
        OrganisationType usedType = OrganisationType.builder()
                .id(1L)
                .code("MINISTERE")
                .libelle("Ministère")
                .actif(true)
                .build();

        when(organisationTypeRepository.findById(1L)).thenReturn(Optional.of(usedType));
        when(organisationRepository.countByTypeOrganisationIgnoreCase("MINISTERE")).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminService.deleteOrganisationType(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(organisationTypeRepository, never()).delete(any());

        when(organisationRepository.countByTypeOrganisationIgnoreCase("MINISTERE")).thenReturn(0L);
        adminService.deleteOrganisationType(1L);
        verify(organisationTypeRepository).delete(usedType);
        verify(organisationTypeRepository).flush();
    }
}
