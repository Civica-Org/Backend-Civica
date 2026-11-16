package cm.civica.backend.controller;

import cm.civica.backend.dto.finance.*;
import cm.civica.backend.service.FinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance")
@Tag(name = "7. Finance, Paiements & Retraits 4 Yeux",
     description = "Module de paiement simulé, allocations inter-institutions, portefeuilles Trésor (InstitutionWallet) et retraits soumis au principe des quatre yeux")
public class FinanceController {

    @Autowired
    private FinanceService financeService;

    @Operation(summary = "Catalogue public des barèmes et répartitions légales",
               description = "Retourne les frais officiels et les clés de répartition inter-organisations pour les démarches CIVICA.")
    @GetMapping("/baremes")
    public ResponseEntity<List<BaremePaiementDto>> getCatalogueBaremes() {
        return ResponseEntity.ok(financeService.getCatalogueBaremes());
    }

    @Operation(summary = "Barème détaillé d'une démarche et variante",
               description = "Calcule le montant total et la répartition prévisionnelle vers chaque portefeuille institutionnel selon la démarche, sa variante et le service administratif choisi.")
    @GetMapping("/baremes/{demarcheCode}")
    public ResponseEntity<BaremePaiementDto> getBaremeByDemarche(
            @PathVariable String demarcheCode,
            @RequestParam(required = false) String variante,
            @RequestParam(required = false) Long serviceId) {
        return ResponseEntity.ok(financeService.getBaremeForDemarche(demarcheCode, variante, serviceId));
    }

    @Operation(summary = "État financier et quittance d'un dossier",
               description = "Retourne le barème applicable, les paiements enregistrés et les quotes-parts réparties pour un numéro de suivi.")
    @GetMapping("/dossiers/{numeroSuivi}/resume")
    public ResponseEntity<DossierFinanceResumeDto> getResumeFinancierDossier(@PathVariable String numeroSuivi) {
        return ResponseEntity.ok(financeService.getResumeFinancierDossier(numeroSuivi));
    }

    @Operation(summary = "Initier et confirmer un paiement simulé (Processus P6)",
               description = "Traite le règlement des droits légaux, émet une quittance unique et crédite de manière atomique les portefeuilles institutionnels via PaymentAllocation.")
    @PostMapping("/paiements/initier")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaiementDto> initierPaiement(@Valid @RequestBody InitierPaiementRequest request) {
        return ResponseEntity.ok(financeService.initierPaiement(request));
    }

    @Operation(summary = "Historique des paiements et quittances du citoyen connecté")
    @GetMapping("/mes-paiements")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PaiementDto>> getMesPaiements() {
        return ResponseEntity.ok(financeService.getMesPaiements());
    }

    @Operation(summary = "Paiements préalables disponibles non encore rattachés à un dossier",
               description = "Retourne les paiements confirmés effectués avant soumission pour reprise dans le formulaire de dépôt.")
    @GetMapping("/pre-paiements/disponibles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PaiementDto>> getMesPaiementsDisponibles(
            @RequestParam(required = false) String demarcheCode) {
        return ResponseEntity.ok(financeService.getMesPaiementsDisponibles(demarcheCode));
    }

    @Operation(summary = "Vue consolidée Trésorerie & Portefeuilles (Responsable / Super Admin)",
               description = "Retourne les soldes disponibles et bloqués des portefeuilles institutionnels, les allocations récentes et les demandes de retrait.")
    @GetMapping("/overview")
    @PreAuthorize("hasAnyAuthority('ROLE_RESPONSABLE', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<FinanceOverviewDto> getFinanceOverview() {
        return ResponseEntity.ok(financeService.getFinanceOverview());
    }

    @Operation(summary = "Initier une demande de retrait financier (Phase 1 des Quatre Yeux — P7)",
               description = "Bloque le montant demandé sur le portefeuille institutionnel en statut DEMANDE en attente de contre-signature par un second responsable.")
    @PostMapping("/withdrawals")
    @PreAuthorize("hasAnyAuthority('ROLE_RESPONSABLE', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<WithdrawalRequestDto> createWithdrawal(@Valid @RequestBody CreateWithdrawalRequest request) {
        return ResponseEntity.ok(financeService.createWithdrawalRequest(request));
    }

    @Operation(summary = "Autoriser et exécuter un retrait financier (Phase 2 des Quatre Yeux — P7)",
               description = "Contre-signe une demande DEMANDE et débite définitivement les fonds bloqués. Échoue (HTTP 403) si l'approbateur est l'initiateur.")
    @PutMapping("/withdrawals/{id}/authorize")
    @PreAuthorize("hasAnyAuthority('ROLE_RESPONSABLE', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<WithdrawalRequestDto> authorizeWithdrawal(
            @PathVariable Long id,
            @RequestBody(required = false) WithdrawalDecisionRequest request) {
        return ResponseEntity.ok(financeService.authorizeWithdrawal(id, request));
    }

    @Operation(summary = "Rejeter un retrait financier et débloquer les fonds (Principe des Quatre Yeux — P7)",
               description = "Refuse une demande DEMANDE et recrédite immédiatement le solde disponible du portefeuille institutionnel.")
    @PutMapping("/withdrawals/{id}/reject")
    @PreAuthorize("hasAnyAuthority('ROLE_RESPONSABLE', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<WithdrawalRequestDto> rejectWithdrawal(
            @PathVariable Long id,
            @Valid @RequestBody WithdrawalDecisionRequest request) {
        return ResponseEntity.ok(financeService.rejectWithdrawal(id, request));
    }
}
