package cm.civica.backend.service;

import cm.civica.backend.dto.finance.*;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {

    @Mock private InstitutionWalletRepository walletRepository;
    @Mock private PaiementRepository paiementRepository;
    @Mock private PaymentAllocationRepository allocationRepository;
    @Mock private WithdrawalRequestRepository withdrawalRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private ServiceAdministratifRepository serviceRepository;
    @Mock private DemarcheRepository demarcheRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private AuthService authService;
    @Mock private DossierSubject dossierSubject;

    @InjectMocks
    private FinanceService financeService;

    private Organisation orgDgsn;
    private Organisation orgMairie;
    private ServiceAdministratif srvMairie;
    private InstitutionWallet walletDgsn;
    private InstitutionWallet walletMairie;
    private Utilisateur citoyen;
    private Utilisateur respMairie;
    private Utilisateur adminTresor;
    private Demarche demCni;
    private Dossier dossierCni;

    @BeforeEach
    void setUp() {
        orgDgsn = Organisation.builder().id(1L).code("DGSN").nom("DGSN").typeOrganisation("POLICE_ETAT").build();
        orgMairie = Organisation.builder().id(2L).code("COMMUNE-YDE-1").nom("Mairie de Yaoundé 1er").typeOrganisation("COLLECTIVITE_TERRITORIALE").build();

        srvMairie = ServiceAdministratif.builder()
                .id(20L).code("SR-ETAT-CIVIL").nom("État Civil").organisation(orgMairie).actif(true).build();

        walletDgsn = InstitutionWallet.builder()
                .id(100L).organisation(orgDgsn).numeroCompteTresor("TRESOR-CM-DGSN-001")
                .soldeDisponible(BigDecimal.ZERO).soldeBloque(BigDecimal.ZERO)
                .totalEncaisse(BigDecimal.ZERO).totalDecaisse(BigDecimal.ZERO).devise("XAF").build();

        walletMairie = InstitutionWallet.builder()
                .id(101L).organisation(orgMairie).numeroCompteTresor("TRESOR-CM-COMMUNE-002")
                .soldeDisponible(BigDecimal.ZERO).soldeBloque(BigDecimal.ZERO)
                .totalEncaisse(BigDecimal.ZERO).totalDecaisse(BigDecimal.ZERO).devise("XAF").build();

        citoyen = Utilisateur.builder().id(10L).email("citoyen@civica.cm").nom("Kamga").prenom("Arnaud").role(Role.ROLE_CITOYEN).build();
        respMairie = Utilisateur.builder().id(11L).email("resp@civica.cm").nom("Ngo").prenom("Marie").role(Role.ROLE_RESPONSABLE).service(srvMairie).build();
        adminTresor = Utilisateur.builder().id(12L).email("admin@civica.cm").nom("Admin").prenom("Civica").role(Role.ROLE_SUPER_ADMIN).build();

        demCni = Demarche.builder().id(1L).code("DEM-CNI").titre("Carte Nationale d'Identité").delaiIndicatifJours(14).actif(true).build();
        dossierCni = Dossier.builder()
                .id(50L).numeroSuivi("CIVICA-2026-11-15-DOS-001")
                .citoyen(citoyen).demarche(demCni).serviceCourant(srvMairie).statut(StatutDossier.SOUMIS).build();
    }

    @Test
    void testBaremeEtAllocationExacteCni() {
        when(demarcheRepository.findByCode("DEM-CNI")).thenReturn(Optional.of(dossierCni.getDemarche()));
        when(organisationRepository.findByCode("DGSN")).thenReturn(Optional.of(orgDgsn));
        when(organisationRepository.findByCode("COMMUNE-YDE-1")).thenReturn(Optional.of(orgMairie));

        BaremePaiementDto bareme = financeService.getBaremeForDemarche("DEM-CNI", "STANDARD");

        assertEquals(new BigDecimal("10000.00"), bareme.getMontantTotal());
        assertEquals(2, bareme.getRepartitionPrevue().size());
        assertEquals(new BigDecimal("7000.00"), bareme.getRepartitionPrevue().get(0).getMontantPrevu());
        assertEquals(new BigDecimal("3000.00"), bareme.getRepartitionPrevue().get(1).getMontantPrevu());
    }

    @Test
    void testInitierPaiementCreditePortefeuilles() {
        when(authService.getCurrentUser()).thenReturn(citoyen);
        when(dossierRepository.findByNumeroSuiviWithPiecesAndDemarche("CIVICA-2026-11-15-DOS-001")).thenReturn(Optional.of(dossierCni));
        when(paiementRepository.existsByDossierIdAndStatut(50L, "CONFIRME")).thenReturn(false);
        when(demarcheRepository.findByCode("DEM-CNI")).thenReturn(Optional.of(dossierCni.getDemarche()));
        when(organisationRepository.findByCode("DGSN")).thenReturn(Optional.of(orgDgsn));
        when(organisationRepository.findByCode("COMMUNE-YDE-1")).thenReturn(Optional.of(orgMairie));
        when(walletRepository.findByOrganisationId(1L)).thenReturn(Optional.of(walletDgsn));
        when(walletRepository.findByOrganisationId(2L)).thenReturn(Optional.of(walletMairie));
        when(walletRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(walletDgsn));
        when(walletRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(walletMairie));
        when(paiementRepository.save(any(Paiement.class))).thenAnswer(inv -> {
            Paiement p = inv.getArgument(0);
            p.setId(77L);
            return p;
        });

        InitierPaiementRequest req = InitierPaiementRequest.builder()
                .numeroSuivi("CIVICA-2026-11-15-DOS-001")
                .modePaiement("MOMO")
                .numeroInstrument("+237677112233")
                .build();

        PaiementDto result = financeService.initierPaiement(req);

        assertEquals("CONFIRME", result.getStatut());
        assertEquals("MOMO", result.getModePaiement());
        assertNotNull(result.getQuittanceNumero());
        assertEquals(2, result.getAllocations().size());
        assertEquals(new BigDecimal("7000.00"), walletDgsn.getSoldeDisponible());
        assertEquals(new BigDecimal("3000.00"), walletMairie.getSoldeDisponible());
    }

    @Test
    void testPrePaiementAvantSoumissionAvecCompteBancaireEtRoutageService() {
        Organisation orgDouala = Organisation.builder()
                .id(3L).code("COMMUNE-DLA-1").nom("Mairie de Douala 1er")
                .typeOrganisation("COLLECTIVITE_TERRITORIALE").build();
        ServiceAdministratif srvDouala = ServiceAdministratif.builder()
                .id(30L).code("SR-DLA-EC").nom("État Civil Douala 1er")
                .organisation(orgDouala).actif(true).build();
        InstitutionWallet walletDouala = InstitutionWallet.builder()
                .id(102L).organisation(orgDouala).numeroCompteTresor("TRESOR-CM-DLA-003")
                .soldeDisponible(BigDecimal.ZERO).soldeBloque(BigDecimal.ZERO)
                .totalEncaisse(BigDecimal.ZERO).totalDecaisse(BigDecimal.ZERO).devise("XAF").build();

        when(authService.getCurrentUser()).thenReturn(citoyen);
        when(demarcheRepository.findByCode("DEM-CNI")).thenReturn(Optional.of(demCni));
        when(serviceRepository.findById(30L)).thenReturn(Optional.of(srvDouala));
        when(organisationRepository.findByCode("DGSN")).thenReturn(Optional.of(orgDgsn));
        when(organisationRepository.findByCode("COMMUNE-DLA-1")).thenReturn(Optional.of(orgDouala));
        when(walletRepository.findByOrganisationId(1L)).thenReturn(Optional.of(walletDgsn));
        when(walletRepository.findByOrganisationId(3L)).thenReturn(Optional.of(walletDouala));
        when(walletRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(walletDgsn));
        when(walletRepository.findByIdForUpdate(102L)).thenReturn(Optional.of(walletDouala));
        when(paiementRepository.save(any(Paiement.class))).thenAnswer(inv -> {
            Paiement p = inv.getArgument(0);
            p.setId(88L);
            return p;
        });

        InitierPaiementRequest req = InitierPaiementRequest.builder()
                .demarcheCode("DEM-CNI")
                .serviceId(30L)
                .modePaiement("COMPTE_BANCAIRE")
                .numeroInstrument("CM2110005000013214567890188")
                .build();

        PaiementDto prePay = financeService.initierPaiement(req);

        assertEquals("CONFIRME", prePay.getStatut());
        assertNull(prePay.getNumeroSuivi());
        assertEquals(30L, prePay.getServiceId());
        assertEquals("COMPTE_BANCAIRE", prePay.getModePaiement());
        assertTrue(prePay.getInstrumentMasque().startsWith("Compte •••• "));
        assertEquals(new BigDecimal("7000.00"), walletDgsn.getSoldeDisponible());
        assertEquals(new BigDecimal("3000.00"), walletDouala.getSoldeDisponible());
        assertEquals(BigDecimal.ZERO, walletMairie.getSoldeDisponible());

        BaremePaiementDto bareme = financeService.getBaremeForDemarche("DEM-CNI", "STANDARD", 30L);

        // Soumission sans référence -> 402 PAYMENT_REQUIRED
        BusinessException unpaidEx = assertThrows(BusinessException.class,
                () -> financeService.validatePrePaymentForSubmission(citoyen, null, "DEM-CNI", bareme, srvDouala));
        assertEquals(HttpStatus.PAYMENT_REQUIRED, unpaidEx.getStatus());

        // Validation avec la transaction confirmée -> réussit puis rattachement au dossier
        Paiement entity = Paiement.builder()
                .id(88L)
                .referenceTransaction(prePay.getReferenceTransaction())
                .quittanceNumero(prePay.getQuittanceNumero())
                .demarcheCode("DEM-CNI")
                .serviceId(30L)
                .citoyen(citoyen)
                .montantTotal(new BigDecimal("10000.00"))
                .modePaiement("COMPTE_BANCAIRE")
                .statut("CONFIRME")
                .build();
        when(paiementRepository.findByReferenceTransaction(prePay.getReferenceTransaction())).thenReturn(Optional.of(entity));

        Paiement validated = financeService.validatePrePaymentForSubmission(
                citoyen, prePay.getReferenceTransaction(), "DEM-CNI", bareme, srvDouala
        );
        assertEquals(88L, validated.getId());

        financeService.linkPrePaymentToDossier(validated, dossierCni);
        assertEquals(dossierCni, validated.getDossier());
    }

    @Test
    void testPrincipeQuatreYeuxInterditAutoApprobationEtAutoriseTiers() {
        walletMairie.setSoldeDisponible(new BigDecimal("10000.00"));
        walletMairie.setTotalEncaisse(new BigDecimal("10000.00"));

        when(authService.getCurrentUser()).thenReturn(respMairie);
        when(walletRepository.findByOrganisationId(2L)).thenReturn(Optional.of(walletMairie));
        when(walletRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(walletMairie));
        when(withdrawalRepository.save(any(WithdrawalRequest.class))).thenAnswer(inv -> {
            WithdrawalRequest wr = inv.getArgument(0);
            wr.setId(500L);
            return wr;
        });

        CreateWithdrawalRequest createReq = CreateWithdrawalRequest.builder()
                .montant(new BigDecimal("4000.00"))
                .compteBeneficiaire("CM21-10005-00001-32145678901-88")
                .motif("Dotation registres état civil Q4")
                .build();

        WithdrawalRequestDto created = financeService.createWithdrawalRequest(createReq);
        assertEquals("DEMANDE", created.getStatut());
        assertEquals(new BigDecimal("6000.00"), walletMairie.getSoldeDisponible());
        assertEquals(new BigDecimal("4000.00"), walletMairie.getSoldeBloque());
        assertFalse(created.isCanCurrentUserDecide());

        WithdrawalRequest entity = WithdrawalRequest.builder()
                .id(500L)
                .referenceDemande(created.getReferenceDemande())
                .wallet(walletMairie)
                .organisation(orgMairie)
                .montant(new BigDecimal("4000.00"))
                .compteBeneficiaire(createReq.getCompteBeneficiaire())
                .motif(createReq.getMotif())
                .statut("DEMANDE")
                .requestedBy(respMairie)
                .build();

        when(withdrawalRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(entity));

        // 1. L'initiateur essaie d'approuver sa propre demande -> REJETÉ (403)
        BusinessException ex = assertThrows(BusinessException.class,
                () -> financeService.authorizeWithdrawal(500L, new WithdrawalDecisionRequest("Auto-validation")));
        assertTrue(ex.getMessage().contains("Double validation requise"));

        // 2. Un second valideur distinct (adminTresor) approuve -> EXÉCUTÉ
        when(authService.getCurrentUser()).thenReturn(adminTresor);
        WithdrawalRequestDto executed = financeService.authorizeWithdrawal(500L, new WithdrawalDecisionRequest("Conforme pièce budgétaire"));
        assertEquals("EXECUTE", executed.getStatut());
        assertEquals(BigDecimal.ZERO.setScale(2), walletMairie.getSoldeBloque());
        assertEquals(new BigDecimal("4000.00"), walletMairie.getTotalDecaisse());
        assertEquals(adminTresor.getId(), executed.getAuthorizedById());
    }
}
