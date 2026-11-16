package cm.civica.backend.service;

import cm.civica.backend.dto.DepotDossierRequest;
import cm.civica.backend.dto.DossierResponseDto;
import cm.civica.backend.dto.finance.BaremePaiementDto;
import cm.civica.backend.dto.finance.PaiementDto;
import cm.civica.backend.enums.Role;
import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.exception.BusinessException;
import cm.civica.backend.model.*;
import cm.civica.backend.pattern.observer.DossierSubject;
import cm.civica.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CitoyenServiceTest {

    @Mock private DossierRepository dossierRepository;
    @Mock private DemarcheRepository demarcheRepository;
    @Mock private ServiceAdministratifRepository serviceRepository;
    @Mock private PieceJustificativeRepository pieceRepository;
    @Mock private HistoriqueDossierRepository historiqueRepository;
    @Mock private AuthService authService;
    @Mock private FileStorageService fileStorageService;
    @Mock private EmailService emailService;
    @Mock private DossierSubject dossierSubject;
    @Mock private FinanceService financeService;

    @InjectMocks
    private CitoyenService citoyenService;

    private Utilisateur citoyen;
    private Organisation orgDgsn;
    private ServiceAdministratif srvDgsn;
    private Demarche demCni;
    private Demarche demActeNaiss;

    @BeforeEach
    void setUp() {
        citoyen = Utilisateur.builder()
                .id(10L).email("citoyen@civica.cm").nom("Kamga").prenom("Arnaud")
                .role(Role.ROLE_CITOYEN).build();

        orgDgsn = Organisation.builder()
                .id(1L).code("DGSN").nom("DGSN").typeOrganisation("POLICE_ETAT").build();

        srvDgsn = ServiceAdministratif.builder()
                .id(15L).code("SR-CNI").nom("Enrôlement Biométrique CNI")
                .organisation(orgDgsn).actif(true).build();

        demCni = Demarche.builder()
                .id(1L).code("DEM-CNI").titre("Carte Nationale d'Identité")
                .delaiIndicatifJours(14).actif(true).build();

        demActeNaiss = Demarche.builder()
                .id(2L).code("DEM-ACTE-NAISS").titre("Acte de Naissance")
                .delaiIndicatifJours(7).actif(true).build();
    }

    @Test
    void testDepotDossierPayantBloqueSiNonPaye() {
        BaremePaiementDto baremePayant = BaremePaiementDto.builder()
                .demarcheCode("DEM-CNI")
                .montantTotal(new BigDecimal("10000.00"))
                .gratuit(false)
                .build();

        when(authService.getCurrentUser()).thenReturn(citoyen);
        when(demarcheRepository.findByCode("DEM-CNI")).thenReturn(Optional.of(demCni));
        when(financeService.resolveDefaultServiceForDemarche("DEM-CNI")).thenReturn(srvDgsn);
        when(financeService.getBaremeForDemarche("DEM-CNI", "STANDARD", 15L)).thenReturn(baremePayant);
        when(financeService.validatePrePaymentForSubmission(citoyen, null, "DEM-CNI", baremePayant, srvDgsn))
                .thenThrow(new BusinessException("Paiement préalable obligatoire", HttpStatus.PAYMENT_REQUIRED));

        DepotDossierRequest request = new DepotDossierRequest("DEM-CNI", null, "NORMALE", "STANDARD", null);

        BusinessException ex = assertThrows(BusinessException.class, () -> citoyenService.deposerDossier(request));
        assertEquals(HttpStatus.PAYMENT_REQUIRED, ex.getStatus());
        verify(dossierRepository, never()).save(any());
        verify(financeService, never()).linkPrePaymentToDossier(any(), any());
    }

    @Test
    void testDepotDossierPayantAvecPrePaiementValideEtRoutageParDefaut() {
        BaremePaiementDto baremePayant = BaremePaiementDto.builder()
                .demarcheCode("DEM-CNI")
                .varianteCircuit("STANDARD")
                .montantTotal(new BigDecimal("10000.00"))
                .gratuit(false)
                .build();

        Paiement prePaiement = Paiement.builder()
                .id(99L)
                .referenceTransaction("TX-CIV-20261116-ABCDEF")
                .quittanceNumero("QUIT-CM-20261116-123456")
                .demarcheCode("DEM-CNI")
                .serviceId(15L)
                .citoyen(citoyen)
                .montantTotal(new BigDecimal("10000.00"))
                .modePaiement("OM")
                .statut("CONFIRME")
                .build();

        PaiementDto prePaiementDto = PaiementDto.builder()
                .id(99L)
                .referenceTransaction("TX-CIV-20261116-ABCDEF")
                .quittanceNumero("QUIT-CM-20261116-123456")
                .demarcheCode("DEM-CNI")
                .serviceId(15L)
                .montantTotal(new BigDecimal("10000.00"))
                .devise("XAF")
                .modePaiement("OM")
                .instrumentMasque("OM •••• 1122")
                .statut("CONFIRME")
                .build();

        when(authService.getCurrentUser()).thenReturn(citoyen);
        when(demarcheRepository.findByCode("DEM-CNI")).thenReturn(Optional.of(demCni));
        when(financeService.resolveDefaultServiceForDemarche("DEM-CNI")).thenReturn(srvDgsn);
        when(financeService.getBaremeForDemarche("DEM-CNI", "STANDARD", 15L)).thenReturn(baremePayant);
        when(financeService.validatePrePaymentForSubmission(
                citoyen, "TX-CIV-20261116-ABCDEF", "DEM-CNI", baremePayant, srvDgsn
        )).thenReturn(prePaiement);
        when(financeService.toPaiementDto(prePaiement)).thenReturn(prePaiementDto);
        when(dossierRepository.countByNumeroSuiviStartingWith(anyString())).thenReturn(0L);
        when(dossierRepository.existsByNumeroSuivi(anyString())).thenReturn(false);
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> {
            Dossier d = inv.getArgument(0);
            d.setId(500L);
            return d;
        });
        when(pieceRepository.findByDossierId(500L)).thenReturn(List.of());
        when(historiqueRepository.findByDossierIdOrderByTimestampDesc(500L)).thenReturn(List.of());

        DepotDossierRequest request = new DepotDossierRequest(
                "DEM-CNI", null, "URGENTE", "STANDARD", "TX-CIV-20261116-ABCDEF"
        );

        DossierResponseDto response = citoyenService.deposerDossier(request);

        assertNotNull(response);
        assertEquals(15L, response.getServiceCourantId());
        assertEquals("DGSN", response.getOrganisationNom());
        assertEquals(StatutDossier.SOUMIS, response.getStatut());
        assertTrue(response.isPaye());
        assertNotNull(response.getPaiementConfirme());
        assertEquals("QUIT-CM-20261116-123456", response.getPaiementConfirme().getQuittanceNumero());
        verify(financeService).linkPrePaymentToDossier(eq(prePaiement), any(Dossier.class));
        verify(dossierSubject).notifyObservers(any());
    }

    @Test
    void testGetDossierByNumeroSuiviInclutInfosPaiement() {
        Dossier dossier = Dossier.builder()
                .id(600L)
                .numeroSuivi("CIVICA-2026-11-16-DOS-001")
                .citoyen(citoyen)
                .demarche(demCni)
                .serviceCourant(srvDgsn)
                .statut(StatutDossier.SOUMIS)
                .priorite("NORMALE")
                .build();

        PaiementDto paiementDto = PaiementDto.builder()
                .id(12L)
                .referenceTransaction("TX-CIV-20261116-999999")
                .quittanceNumero("QUIT-CM-20261116-999999")
                .numeroSuivi("CIVICA-2026-11-16-DOS-001")
                .montantTotal(new BigDecimal("10000.00"))
                .devise("XAF")
                .modePaiement("MOMO")
                .instrumentMasque("MOMO •••• 1122")
                .statut("CONFIRME")
                .build();

        when(dossierRepository.findByNumeroSuiviWithPiecesAndDemarche("CIVICA-2026-11-16-DOS-001"))
                .thenReturn(Optional.of(dossier));
        when(financeService.getConfirmedPaiementForDossier(600L)).thenReturn(Optional.of(paiementDto));
        when(pieceRepository.findByDossierId(600L)).thenReturn(List.of());
        when(historiqueRepository.findByDossierIdOrderByTimestampDesc(600L)).thenReturn(List.of());

        DossierResponseDto dto = citoyenService.getDossierByNumeroSuivi("CIVICA-2026-11-16-DOS-001");

        assertTrue(dto.isPaye());
        assertNotNull(dto.getPaiementConfirme());
        assertEquals("QUIT-CM-20261116-999999", dto.getPaiementConfirme().getQuittanceNumero());
        assertEquals("MOMO", dto.getPaiementConfirme().getModePaiement());
        assertEquals(new BigDecimal("10000.00"), dto.getPaiementConfirme().getMontantTotal());
    }

    @Test
    void testDepotDossierExonereNeRequiertPasDePaiement() {
        BaremePaiementDto baremeGratuit = BaremePaiementDto.builder()
                .demarcheCode("DEM-ACTE-NAISS")
                .varianteCircuit("NORMALE")
                .montantTotal(BigDecimal.ZERO.setScale(2))
                .gratuit(true)
                .build();

        when(authService.getCurrentUser()).thenReturn(citoyen);
        when(demarcheRepository.findByCode("DEM-ACTE-NAISS")).thenReturn(Optional.of(demActeNaiss));
        when(financeService.resolveDefaultServiceForDemarche("DEM-ACTE-NAISS")).thenReturn(srvDgsn);
        when(financeService.getBaremeForDemarche("DEM-ACTE-NAISS", "NORMALE", 15L)).thenReturn(baremeGratuit);
        when(dossierRepository.countByNumeroSuiviStartingWith(anyString())).thenReturn(0L);
        when(dossierRepository.existsByNumeroSuivi(anyString())).thenReturn(false);
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> {
            Dossier d = inv.getArgument(0);
            d.setId(501L);
            return d;
        });
        when(pieceRepository.findByDossierId(501L)).thenReturn(List.of());
        when(historiqueRepository.findByDossierIdOrderByTimestampDesc(501L)).thenReturn(List.of());

        DepotDossierRequest request = new DepotDossierRequest("DEM-ACTE-NAISS", null, "NORMALE", "NORMALE", null);
        DossierResponseDto response = citoyenService.deposerDossier(request);

        assertEquals("DEM-ACTE-NAISS", response.getDemarcheCode());
        verify(financeService, never()).validatePrePaymentForSubmission(any(), any(), any(), any(), any());
        verify(financeService, never()).linkPrePaymentToDossier(any(), any());
    }
}
