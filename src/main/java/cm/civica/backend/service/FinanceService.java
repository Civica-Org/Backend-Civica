package cm.civica.backend.service;

import cm.civica.backend.dto.finance.*;
import cm.civica.backend.enums.Role;
import cm.civica.backend.exception.BusinessException;
import cm.civica.backend.model.*;
import cm.civica.backend.pattern.observer.DossierEvent;
import cm.civica.backend.pattern.observer.DossierSubject;
import cm.civica.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service financier CIVICA (Livrables 14 & 15) :
 * - L14 : Paiements simulés des démarches, génération de quittances, répartition multi-institutions
 *         (PaymentAllocation) et portefeuilles internes (InstitutionWallet).
 * - L15 : Décaissements institutionnels soumis au principe des quatre yeux (authorizedBy != requestedBy).
 */
@Service
public class FinanceService {

    private static final Logger log = LoggerFactory.getLogger(FinanceService.class);
    private static final String DEVISE_DEFAUT = "XAF";

    @Autowired
    private InstitutionWalletRepository walletRepository;

    @Autowired
    private PaiementRepository paiementRepository;

    @Autowired
    private PaymentAllocationRepository allocationRepository;

    @Autowired
    private WithdrawalRequestRepository withdrawalRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private DemarcheRepository demarcheRepository;

    @Autowired
    private ServiceAdministratifRepository serviceRepository;

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private DossierSubject dossierSubject;

    /**
     * Provisionne automatiquement un portefeuille Trésor pour toute organisation existante au démarrage.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeInstitutionWallets() {
        List<Organisation> orgs = organisationRepository.findAll();
        for (Organisation org : orgs) {
            ensureWalletForOrganisation(org);
        }
        log.info(">>> [FINANCE] {} portefeuille(s) institutionnel(s) vérifié(s)/provisionné(s).", orgs.size());
    }

    @Transactional
    public InstitutionWallet ensureWalletForOrganisation(Organisation org) {
        return walletRepository.findByOrganisationId(org.getId()).orElseGet(() -> {
            String numeroTresor = String.format("TRESOR-CM-%s-%03d",
                    org.getCode().replaceAll("[^A-Z0-9-]", "").toUpperCase(),
                    org.getId());
            InstitutionWallet created = InstitutionWallet.builder()
                    .organisation(org)
                    .numeroCompteTresor(numeroTresor)
                    .soldeDisponible(BigDecimal.ZERO)
                    .soldeBloque(BigDecimal.ZERO)
                    .totalEncaisse(BigDecimal.ZERO)
                    .totalDecaisse(BigDecimal.ZERO)
                    .devise(DEVISE_DEFAUT)
                    .build();
            return walletRepository.save(created);
        });
    }

    // -------------------------------------------------------------------------
    // 1. BARÈMES OFFICIELS & RÉPARTITION MULTI-INSTITUTIONS (LIVRABLE 14)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BaremePaiementDto> getCatalogueBaremes() {
        List<BaremePaiementDto> list = new ArrayList<>();
        list.add(computeBareme("DEM-CNI", "STANDARD", null));
        list.add(computeBareme("DEM-ACTE-NAISS", "EXTRAIT_COPIE", null));
        list.add(computeBareme("DEM-ACTE-NAISS", "TARDIVE", null));
        list.add(computeBareme("DEM-ACTE-NAISS", "JUDICIAIRE", null));
        list.add(computeBareme("DEM-ACTE-NAISS", "NORMALE", null));
        list.add(computeBareme("DEM-CASIER-JUD", "STANDARD", null));
        return list;
    }

    @Transactional(readOnly = true)
    public BaremePaiementDto getBaremeForDemarche(String demarcheCode, String varianteCircuit) {
        return getBaremeForDemarche(demarcheCode, varianteCircuit, null);
    }

    @Transactional(readOnly = true)
    public BaremePaiementDto getBaremeForDemarche(String demarcheCode, String varianteCircuit, Long serviceId) {
        ServiceAdministratif targetService = resolveServiceForBareme(demarcheCode, serviceId);
        return computeBareme(demarcheCode, varianteCircuit, targetService);
    }

    /**
     * Détermine le service administratif compétent par défaut pour une démarche lorsque le citoyen
     * choisit l'attribution automatique (aligne routage du dossier et ventilation des frais).
     */
    @Transactional(readOnly = true)
    public ServiceAdministratif resolveDefaultServiceForDemarche(String demarcheCode) {
        if (serviceRepository == null) {
            return null;
        }
        String code = demarcheCode != null ? demarcheCode.trim().toUpperCase() : "";
        String preferredOrgCode = switch (code) {
            case "DEM-CNI" -> "DGSN";
            case "DEM-ACTE-NAISS" -> "COMMUNE-YDE-1";
            case "DEM-CASIER-JUD" -> "TPI-YDE";
            default -> null;
        };

        if (preferredOrgCode != null) {
            Optional<Organisation> orgOpt = organisationRepository.findByCode(preferredOrgCode);
            if (orgOpt.isPresent()) {
                List<ServiceAdministratif> orgServices = serviceRepository.findByOrganisationIdAndActifTrue(orgOpt.get().getId());
                if (!orgServices.isEmpty()) {
                    return orgServices.get(0);
                }
            }
        }

        List<ServiceAdministratif> activeServices = serviceRepository.findByActifTrue();
        return activeServices.isEmpty() ? null : activeServices.get(0);
    }

    @Transactional(readOnly = true)
    public List<PaiementDto> getMesPaiementsDisponibles(String demarcheCode) {
        Utilisateur currentUser = authService.getCurrentUser();
        List<Paiement> disponibles;
        if (demarcheCode != null && !demarcheCode.isBlank()) {
            disponibles = paiementRepository.findByCitoyenIdAndDemarcheCodeAndDossierIsNullAndStatutOrderByCreatedAtDesc(
                    currentUser.getId(), demarcheCode.trim().toUpperCase(), "CONFIRME"
            );
        } else {
            disponibles = paiementRepository.findByCitoyenIdAndDossierIsNullAndStatutOrderByCreatedAtDesc(
                    currentUser.getId(), "CONFIRME"
            );
        }
        return disponibles.stream().map(this::toPaiementDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DossierFinanceResumeDto getResumeFinancierDossier(String numeroSuivi) {
        Dossier dossier = dossierRepository.findByNumeroSuiviWithPiecesAndDemarche(numeroSuivi.trim())
                .orElseThrow(() -> BusinessException.notFound("Dossier introuvable : " + numeroSuivi));

        List<Paiement> paiements = paiementRepository.findByDossierNumeroSuiviOrderByCreatedAtDesc(dossier.getNumeroSuivi());
        Paiement confirme = paiements.stream()
                .filter(p -> "CONFIRME".equals(p.getStatut()))
                .findFirst()
                .orElse(null);

        String variante = confirme != null && confirme.getVarianteCircuit() != null
                ? confirme.getVarianteCircuit()
                : defaultVarianteForDemarche(dossier.getDemarche().getCode());

        BaremePaiementDto bareme = computeBareme(dossier.getDemarche().getCode(), variante, dossier.getServiceCourant());

        return DossierFinanceResumeDto.builder()
                .numeroSuivi(dossier.getNumeroSuivi())
                .dejaPaye(confirme != null)
                .bareme(bareme)
                .paiementConfirme(confirme != null ? toPaiementDto(confirme) : null)
                .historiquePaiements(paiements.stream().map(this::toPaiementDto).collect(Collectors.toList()))
                .build();
    }

    // -------------------------------------------------------------------------
    // 2. PAIEMENT SIMULÉ & ALLOCATIONS ATOMIQUES (PROCESSUS BPMN P6)
    // -------------------------------------------------------------------------

    @Transactional
    public PaiementDto initierPaiement(InitierPaiementRequest request) {
        Utilisateur currentUser = authService.getCurrentUser();
        String modePaiement = normalizeModePaiement(request.getModePaiement());

        Dossier dossier = null;
        String demarcheCode;
        ServiceAdministratif targetService;

        if (request.getNumeroSuivi() != null && !request.getNumeroSuivi().isBlank()) {
            dossier = dossierRepository.findByNumeroSuiviWithPiecesAndDemarche(request.getNumeroSuivi().trim())
                    .orElseThrow(() -> BusinessException.notFound("Dossier introuvable : " + request.getNumeroSuivi()));

            boolean isOwner = dossier.getCitoyen().getId().equals(currentUser.getId());
            boolean isStaff = currentUser.getRole() != Role.ROLE_CITOYEN;
            if (!isOwner && !isStaff) {
                throw BusinessException.forbidden("Vous n'êtes pas autorisé à régler ce dossier.");
            }

            if (paiementRepository.existsByDossierIdAndStatut(dossier.getId(), "CONFIRME")) {
                throw new BusinessException(
                        "Ce dossier a déjà fait l'objet d'un paiement confirmé et quittancé.",
                        HttpStatus.CONFLICT
                );
            }

            demarcheCode = dossier.getDemarche().getCode();
            targetService = dossier.getServiceCourant();
        } else {
            if (request.getDemarcheCode() == null || request.getDemarcheCode().isBlank()) {
                throw BusinessException.badRequest("Le code de la démarche (ou un numéro de suivi) est obligatoire pour initier le paiement.");
            }
            Demarche demarche = demarcheRepository.findByCode(request.getDemarcheCode().trim())
                    .orElseThrow(() -> BusinessException.notFound("Démarche introuvable avec le code : " + request.getDemarcheCode()));
            if (!Boolean.TRUE.equals(demarche.getActif())) {
                throw BusinessException.badRequest("Cette démarche administrative est actuellement désactivée.");
            }
            demarcheCode = demarche.getCode();
            targetService = resolveServiceForBareme(demarcheCode, request.getServiceId());
        }

        String variante = (request.getVarianteCircuit() != null && !request.getVarianteCircuit().isBlank())
                ? request.getVarianteCircuit().trim().toUpperCase()
                : defaultVarianteForDemarche(demarcheCode);

        // Éviter la double facturation en pré-soumission si un paiement identique non encore consommé existe déjà
        if (dossier == null && !Boolean.TRUE.equals(request.getSimulerEchec())) {
            Long targetServiceId = targetService != null ? targetService.getId() : null;
            Optional<Paiement> existingAvailable = paiementRepository
                    .findByCitoyenIdAndDemarcheCodeAndDossierIsNullAndStatutOrderByCreatedAtDesc(
                            currentUser.getId(), demarcheCode.toUpperCase(), "CONFIRME"
                    ).stream()
                    .filter(p -> variante.equalsIgnoreCase(
                            p.getVarianteCircuit() != null ? p.getVarianteCircuit() : defaultVarianteForDemarche(demarcheCode)))
                    .filter(p -> Objects.equals(p.getServiceId(), targetServiceId))
                    .findFirst();
            if (existingAvailable.isPresent()) {
                throw new BusinessException(
                        "Un paiement préalable est déjà confirmé pour cette démarche (Quittance : "
                                + existingAvailable.get().getQuittanceNumero()
                                + "). Vous pouvez soumettre votre dossier directement.",
                        HttpStatus.CONFLICT
                );
            }
        }

        BaremePaiementDto bareme = computeBareme(demarcheCode, variante, targetService);
        if (bareme.isGratuit() || bareme.getMontantTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.badRequest("Cette variante de démarche est exonérée de droits légaux (0 XAF).");
        }

        String refTx = generateReference("TX-CIV");
        String maskedInstrument = maskInstrument(modePaiement, request.getNumeroInstrument());
        Utilisateur ownerCitoyen = dossier != null ? dossier.getCitoyen() : currentUser;
        Long persistedServiceId = targetService != null ? targetService.getId() : request.getServiceId();

        // Scénario de test BPMN P6 : passerelle Échec de paiement (ECHOUE) sans créditer les portefeuilles
        if (Boolean.TRUE.equals(request.getSimulerEchec())) {
            Paiement failed = Paiement.builder()
                    .referenceTransaction(refTx)
                    .dossier(dossier)
                    .demarcheCode(demarcheCode)
                    .serviceId(persistedServiceId)
                    .citoyen(ownerCitoyen)
                    .montantTotal(bareme.getMontantTotal())
                    .devise(DEVISE_DEFAUT)
                    .modePaiement(modePaiement)
                    .instrumentMasque(maskedInstrument)
                    .varianteCircuit(variante)
                    .statut("ECHOUE")
                    .build();
            Paiement savedFailed = paiementRepository.save(failed);

            if (dossier != null) {
                dossierSubject.notifyObservers(DossierEvent.of(
                        dossier, dossier.getStatut(), dossier.getStatut(),
                        "PAIEMENT_ECHOUE",
                        String.format("Échec du paiement simulé de %s %s (%s) — Référence %s.",
                                bareme.getMontantTotal().toPlainString(), DEVISE_DEFAUT, formatModeLabel(modePaiement), refTx),
                        currentUser, dossier.getServiceCourant()
                ));
            }

            return toPaiementDto(savedFailed);
        }

        String quittance = generateReference("QUIT-CM");
        Paiement paiement = Paiement.builder()
                .referenceTransaction(refTx)
                .quittanceNumero(quittance)
                .dossier(dossier)
                .demarcheCode(demarcheCode)
                .serviceId(persistedServiceId)
                .citoyen(ownerCitoyen)
                .montantTotal(bareme.getMontantTotal())
                .devise(DEVISE_DEFAUT)
                .modePaiement(modePaiement)
                .instrumentMasque(maskedInstrument)
                .varianteCircuit(variante)
                .statut("CONFIRME")
                .confirmedAt(Instant.now())
                .build();

        // Résoudre toutes les organisations concernées puis verrouiller les wallets dans l'ordre croissant des IDs
        List<AllocationRuleDto> rules = bareme.getRepartitionPrevue();
        List<ResolvedSplit> resolvedSplits = new ArrayList<>();

        for (AllocationRuleDto rule : rules) {
            Organisation targetOrg = organisationRepository.findByCode(rule.getOrganisationCode())
                    .orElseThrow(() -> BusinessException.internalServerError(
                            "Organisation bénéficiaire introuvable lors de la répartition : " + rule.getOrganisationCode()));
            InstitutionWallet wallet = ensureWalletForOrganisation(targetOrg);
            resolvedSplits.add(new ResolvedSplit(wallet.getId(), targetOrg, rule));
        }

        // Verrou pessimiste ordonné par walletId pour garantir l'absence d'interblocage (deadlock)
        Map<Long, InstitutionWallet> lockedWallets = new HashMap<>();
        resolvedSplits.stream()
                .map(s -> s.walletId)
                .distinct()
                .sorted()
                .forEach(wid -> {
                    InstitutionWallet w = walletRepository.findByIdForUpdate(wid)
                            .orElseThrow(() -> BusinessException.notFound("Portefeuille introuvable : " + wid));
                    lockedWallets.put(wid, w);
                });

        BigDecimal allocatedSum = BigDecimal.ZERO;
        for (ResolvedSplit split : resolvedSplits) {
            BigDecimal partAmount = split.rule.getMontantPrevu().setScale(2, RoundingMode.HALF_UP);
            InstitutionWallet wallet = lockedWallets.get(split.walletId);
            wallet.crediterAllocation(partAmount);
            walletRepository.save(wallet);

            PaymentAllocation allocation = PaymentAllocation.builder()
                    .wallet(wallet)
                    .organisation(split.organisation)
                    .libelleQuotePart(split.rule.getLibelleQuotePart())
                    .pourcentage(split.rule.getPourcentage())
                    .montantAlloue(partAmount)
                    .build();
            paiement.ajouterAllocation(allocation);
            allocatedSum = allocatedSum.add(partAmount);
        }

        if (allocatedSum.compareTo(paiement.getMontantTotal().setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw BusinessException.internalServerError(
                    "Incohérence arithmétique lors de la répartition financière : " + allocatedSum + " != " + paiement.getMontantTotal()
            );
        }

        Paiement saved = paiementRepository.save(paiement);

        if (dossier != null) {
            dossierSubject.notifyObservers(DossierEvent.of(
                    dossier, dossier.getStatut(), dossier.getStatut(),
                    "PAIEMENT_CONFIRME",
                    String.format("Paiement confirmé de %s %s (%s). Quittance : %s.",
                            saved.getMontantTotal().toPlainString(), DEVISE_DEFAUT,
                            formatModeLabel(saved.getModePaiement()), saved.getQuittanceNumero()),
                    currentUser, dossier.getServiceCourant()
            ));
        }

        return toPaiementDto(saved);
    }

    /**
     * Valide un paiement préalable lors de la soumission d'un dossier citoyen (obligatoire si frais > 0).
     */
    @Transactional
    public Paiement validatePrePaymentForSubmission(
            Utilisateur citoyen,
            String paiementReference,
            String demarcheCode,
            BaremePaiementDto bareme,
            ServiceAdministratif targetService) {
        if (paiementReference == null || paiementReference.isBlank()) {
            throw new BusinessException(
                    "Le paiement préalable des frais de service (" + bareme.getMontantTotal().toPlainString()
                            + " " + DEVISE_DEFAUT + ") est obligatoire avant de soumettre le dossier.",
                    HttpStatus.PAYMENT_REQUIRED
            );
        }

        Paiement paiement = paiementRepository.findByReferenceTransaction(paiementReference.trim())
                .orElseThrow(() -> BusinessException.notFound("Transaction de paiement introuvable : " + paiementReference));

        if (!paiement.getCitoyen().getId().equals(citoyen.getId())) {
            throw BusinessException.forbidden("Ce paiement n'appartient pas au citoyen connecté.");
        }
        if (!"CONFIRME".equals(paiement.getStatut())) {
            throw new BusinessException(
                    "Le paiement référencé n'est pas confirmé (statut actuel : " + paiement.getStatut() + ").",
                    HttpStatus.PAYMENT_REQUIRED
            );
        }
        if (paiement.getDossier() != null) {
            throw BusinessException.conflict(
                    "Ce paiement est déjà rattaché au dossier " + paiement.getDossier().getNumeroSuivi() + "."
            );
        }
        if (paiement.getDemarcheCode() != null && !paiement.getDemarcheCode().equalsIgnoreCase(demarcheCode)) {
            throw BusinessException.badRequest(
                    "Ce paiement a été effectué pour la démarche " + paiement.getDemarcheCode()
                            + " et ne peut pas être utilisé pour " + demarcheCode + "."
            );
        }
        if (paiement.getMontantTotal().compareTo(bareme.getMontantTotal()) < 0) {
            throw BusinessException.badRequest(String.format(
                    "Montant réglé insuffisant (%s %s) pour la variante choisie (%s %s requis).",
                    paiement.getMontantTotal().toPlainString(), DEVISE_DEFAUT,
                    bareme.getMontantTotal().toPlainString(), DEVISE_DEFAUT
            ));
        }
        if (paiement.getServiceId() != null && targetService != null && !paiement.getServiceId().equals(targetService.getId())) {
            throw BusinessException.badRequest(
                    "Le service sélectionné ne correspond pas au service choisi lors du paiement préalable."
            );
        }
        return paiement;
    }

    @Transactional
    public void linkPrePaymentToDossier(Paiement paiement, Dossier savedDossier) {
        paiement.setDossier(savedDossier);
        Paiement saved = paiementRepository.save(paiement);
        if (dossierSubject != null && savedDossier != null) {
            dossierSubject.notifyObservers(DossierEvent.of(
                    savedDossier, savedDossier.getStatut(), savedDossier.getStatut(),
                    "PAIEMENT_CONFIRME",
                    String.format("Paiement préalable confirmé de %s %s (%s). Quittance : %s.",
                            saved.getMontantTotal().toPlainString(), DEVISE_DEFAUT,
                            formatModeLabel(saved.getModePaiement()), saved.getQuittanceNumero()),
                    saved.getCitoyen(), savedDossier.getServiceCourant()
            ));
        }
    }

    @Transactional(readOnly = true)
    public Optional<PaiementDto> getConfirmedPaiementForDossier(Long dossierId) {
        if (dossierId == null || paiementRepository == null) {
            return Optional.empty();
        }
        return paiementRepository.findFirstByDossierIdAndStatutOrderByCreatedAtDesc(dossierId, "CONFIRME")
                .map(this::toPaiementDto);
    }

    @Transactional(readOnly = true)
    public List<PaiementDto> getMesPaiements() {
        Utilisateur currentUser = authService.getCurrentUser();
        return paiementRepository.findByCitoyenIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(this::toPaiementDto)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // 3. PORTEFEUILLES & RETRAITS FINANCIERS 4 YEUX (LIVRABLE 15 / BPMN P7)
    // -------------------------------------------------------------------------

    @Transactional
    public FinanceOverviewDto getFinanceOverview() {
        Utilisateur user = authService.getCurrentUser();
        // S'assurer que toute organisation créée manuellement possède son portefeuille
        for (Organisation org : organisationRepository.findAll()) {
            ensureWalletForOrganisation(org);
        }

        List<InstitutionWallet> wallets;
        List<WithdrawalRequest> retraits;
        List<PaymentAllocation> allocations;
        List<Paiement> paiements;

        if (user.getRole() == Role.ROLE_SUPER_ADMIN) {
            wallets = walletRepository.findAllByOrderByOrganisationNomAsc();
            retraits = withdrawalRepository.findAllByOrderByCreatedAtDesc();
            allocations = allocationRepository.findTop50ByOrderByCreatedAtDesc();
            paiements = paiementRepository.findTop50ByOrderByCreatedAtDesc();
        } else if (user.getRole() == Role.ROLE_RESPONSABLE) {
            Organisation org = requireUserOrganisation(user);
            InstitutionWallet wallet = ensureWalletForOrganisation(org);
            wallets = List.of(wallet);
            retraits = withdrawalRepository.findByOrganisationIdOrderByCreatedAtDesc(org.getId());
            allocations = allocationRepository.findTop30ByWalletIdOrderByCreatedAtDesc(wallet.getId());
            paiements = allocations.stream()
                    .map(PaymentAllocation::getPaiement)
                    .distinct()
                    .limit(30)
                    .collect(Collectors.toList());
        } else {
            throw BusinessException.forbidden("Accès réservé aux responsables financiers et administrateurs.");
        }

        BigDecimal totalEncaisse = sumField(wallets, InstitutionWallet::getTotalEncaisse);
        BigDecimal totalDisponible = sumField(wallets, InstitutionWallet::getSoldeDisponible);
        BigDecimal totalBloque = sumField(wallets, InstitutionWallet::getSoldeBloque);
        BigDecimal totalDecaisse = sumField(wallets, InstitutionWallet::getTotalDecaisse);
        long pendingCount = retraits.stream().filter(r -> "DEMANDE".equals(r.getStatut())).count();
        long paidCount = paiements.stream().filter(p -> "CONFIRME".equals(p.getStatut())).count();

        return FinanceOverviewDto.builder()
                .totalEncaisseGlobal(totalEncaisse)
                .totalDisponibleGlobal(totalDisponible)
                .totalBloqueGlobal(totalBloque)
                .totalDecaisseGlobal(totalDecaisse)
                .nombrePaiementsConfirmes(paidCount)
                .nombreRetraitsEnAttente(pendingCount)
                .wallets(wallets.stream().map(this::toWalletDto).collect(Collectors.toList()))
                .retraits(retraits.stream().map(w -> toWithdrawalDto(w, user)).collect(Collectors.toList()))
                .allocationsRecentes(allocations.stream().map(this::toAllocationDto).collect(Collectors.toList()))
                .paiementsRecents(paiements.stream().map(this::toPaiementDto).collect(Collectors.toList()))
                .build();
    }

    @Transactional
    public WithdrawalRequestDto createWithdrawalRequest(CreateWithdrawalRequest request) {
        Utilisateur initiateur = authService.getCurrentUser();
        Long targetWalletId = resolveTargetWalletId(initiateur, request.getWalletId());

        InstitutionWallet wallet = walletRepository.findByIdForUpdate(targetWalletId)
                .orElseThrow(() -> BusinessException.notFound("Portefeuille institutionnel introuvable : " + targetWalletId));

        verifyWalletScope(initiateur, wallet.getOrganisation());

        BigDecimal montant = request.getMontant().setScale(2, RoundingMode.HALF_UP);
        if (wallet.getSoldeDisponible().compareTo(montant) < 0) {
            throw BusinessException.badRequest(String.format(
                    "Solde disponible insuffisant (%s %s disponible pour %s %s demandé).",
                    wallet.getSoldeDisponible().toPlainString(), wallet.getDevise(),
                    montant.toPlainString(), wallet.getDevise()
            ));
        }

        // Phase 1 du principe 4 yeux : réserver les fonds en solde_bloque
        wallet.bloquerPourRetrait(montant);
        walletRepository.save(wallet);

        WithdrawalRequest wr = WithdrawalRequest.builder()
                .referenceDemande(generateReference("RET-CM"))
                .wallet(wallet)
                .organisation(wallet.getOrganisation())
                .montant(montant)
                .compteBeneficiaire(request.getCompteBeneficiaire().trim())
                .motif(request.getMotif().trim())
                .statut("DEMANDE")
                .requestedBy(initiateur)
                .build();

        WithdrawalRequest saved = withdrawalRepository.save(wr);
        return toWithdrawalDto(saved, initiateur);
    }

    @Transactional
    public WithdrawalRequestDto authorizeWithdrawal(Long withdrawalId, WithdrawalDecisionRequest request) {
        Utilisateur approbateur = authService.getCurrentUser();
        WithdrawalRequest wr = withdrawalRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> BusinessException.notFound("Demande de retrait introuvable : " + withdrawalId));

        verifyWalletScope(approbateur, wr.getOrganisation());

        if (!"DEMANDE".equals(wr.getStatut())) {
            throw new BusinessException("Cette demande a déjà été clôturée avec le statut : " + wr.getStatut(), HttpStatus.CONFLICT);
        }

        // CONTRÔLE STRICT DU PRINCIPE DES QUATRE YEUX (Livrable 15)
        if (wr.getRequestedBy().getId().equals(approbateur.getId())) {
            throw BusinessException.forbidden(
                    "Double validation requise : l'initiateur du retrait (" +
                    approbateur.getPrenom() + " " + approbateur.getNom() +
                    ") ne peut pas autoriser sa propre demande. Un second valideur habilité est requis."
            );
        }

        InstitutionWallet wallet = walletRepository.findByIdForUpdate(wr.getWallet().getId())
                .orElseThrow(() -> BusinessException.notFound("Portefeuille introuvable"));

        wallet.executerRetraitBloque(wr.getMontant());
        walletRepository.save(wallet);

        wr.setStatut("EXECUTE");
        wr.setAuthorizedBy(approbateur);
        wr.setDecidedAt(Instant.now());
        wr.setCommentaireDecision(
                request != null && request.getCommentaireDecision() != null && !request.getCommentaireDecision().isBlank()
                        ? request.getCommentaireDecision().trim()
                        : "Double contrôle validé — virement exécuté vers le compte bénéficiaire."
        );

        return toWithdrawalDto(withdrawalRepository.save(wr), approbateur);
    }

    @Transactional
    public WithdrawalRequestDto rejectWithdrawal(Long withdrawalId, WithdrawalDecisionRequest request) {
        Utilisateur valideur = authService.getCurrentUser();
        if (request == null || request.getCommentaireDecision() == null || request.getCommentaireDecision().isBlank()) {
            throw BusinessException.badRequest("Un motif justificatif est obligatoire pour rejeter une demande de retrait.");
        }

        WithdrawalRequest wr = withdrawalRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> BusinessException.notFound("Demande de retrait introuvable : " + withdrawalId));

        verifyWalletScope(valideur, wr.getOrganisation());

        if (!"DEMANDE".equals(wr.getStatut())) {
            throw new BusinessException("Cette demande a déjà été clôturée avec le statut : " + wr.getStatut(), HttpStatus.CONFLICT);
        }

        if (wr.getRequestedBy().getId().equals(valideur.getId())) {
            throw BusinessException.forbidden(
                    "Double validation requise : la décision de validation ou de rejet doit être prise par un second contrôleur distinct."
            );
        }

        InstitutionWallet wallet = walletRepository.findByIdForUpdate(wr.getWallet().getId())
                .orElseThrow(() -> BusinessException.notFound("Portefeuille introuvable"));

        wallet.debloquerSuiteRefus(wr.getMontant());
        walletRepository.save(wallet);

        wr.setStatut("REFUSE");
        wr.setAuthorizedBy(valideur);
        wr.setDecidedAt(Instant.now());
        wr.setCommentaireDecision(request.getCommentaireDecision().trim());

        return toWithdrawalDto(withdrawalRepository.save(wr), valideur);
    }

    // -------------------------------------------------------------------------
    // HELPERS MÉTIER & MAPPERS
    // -------------------------------------------------------------------------

    private ServiceAdministratif resolveServiceForBareme(String demarcheCode, Long serviceId) {
        if (serviceId != null) {
            if (serviceRepository == null) {
                return null;
            }
            ServiceAdministratif service = serviceRepository.findById(serviceId)
                    .orElseThrow(() -> BusinessException.notFound("Service administratif introuvable : " + serviceId));
            if (!Boolean.TRUE.equals(service.getActif())) {
                throw BusinessException.badRequest("Ce service administratif est actuellement inactif.");
            }
            return service;
        }
        return resolveDefaultServiceForDemarche(demarcheCode);
    }

    private BaremePaiementDto computeBareme(String demarcheCode, String varianteRaw, ServiceAdministratif targetService) {
        String code = demarcheCode != null ? demarcheCode.trim().toUpperCase() : "CUSTOM";
        String variante = (varianteRaw != null && !varianteRaw.isBlank())
                ? varianteRaw.trim().toUpperCase()
                : defaultVarianteForDemarche(code);

        String titre = demarcheRepository.findByCode(code)
                .map(Demarche::getTitre)
                .orElse(code);

        Organisation selectedOrg = targetService != null ? targetService.getOrganisation() : null;
        String selectedOrgType = selectedOrg != null && selectedOrg.getTypeOrganisation() != null
                ? selectedOrg.getTypeOrganisation().trim().toUpperCase()
                : "";
        String communeOrgCode = "COLLECTIVITE_TERRITORIALE".equals(selectedOrgType)
                ? selectedOrg.getCode()
                : "COMMUNE-YDE-1";
        String policeOrgCode = "POLICE_ETAT".equals(selectedOrgType)
                ? selectedOrg.getCode()
                : "DGSN";
        String justiceOrgCode = "JUSTICE".equals(selectedOrgType)
                ? selectedOrg.getCode()
                : "TPI-YDE";

        BigDecimal total;
        String refLegale;
        List<RawRule> rawRules = new ArrayList<>();

        switch (code) {
            case "DEM-CNI" -> {
                total = new BigDecimal("10000.00");
                refLegale = "Loi de Finances — Droits de timbre biométrique CNI (10 000 XAF)";
                rawRules.add(new RawRule(policeOrgCode, "Timbre biométrique & production CNI", new BigDecimal("70.00")));
                rawRules.add(new RawRule(communeOrgCode, "Enrôlement communal & vérification état civil", new BigDecimal("30.00")));
            }
            case "DEM-ACTE-NAISS" -> {
                switch (variante) {
                    case "NORMALE" -> {
                        total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                        refLegale = "Ordonnance n° 81-02 / Loi 2011/011 — Déclaration normale <= 90j (Exonérée)";
                    }
                    case "TARDIVE" -> {
                        total = new BigDecimal("1500.00");
                        refLegale = "Ordonnance n° 81-02 — Déclaration tardive sur réquisition du Parquet (91-365j)";
                        rawRules.add(new RawRule(communeOrgCode, "Dressement & inscription sur registre communal", new BigDecimal("60.00")));
                        rawRules.add(new RawRule(justiceOrgCode, "Frais de réquisition du Procureur de la République", new BigDecimal("40.00")));
                    }
                    case "JUDICIAIRE" -> {
                        total = new BigDecimal("5000.00");
                        refLegale = "Ordonnance n° 81-02 — Jugement supplétif d'acte de naissance (> 365j)";
                        rawRules.add(new RawRule(justiceOrgCode, "Droits de greffe & audience de jugement supplétif", new BigDecimal("70.00")));
                        rawRules.add(new RawRule(communeOrgCode, "Transcription du dispositif sur registre d'état civil", new BigDecimal("30.00")));
                    }
                    default -> {
                        variante = "EXTRAIT_COPIE";
                        total = new BigDecimal("1000.00");
                        refLegale = "Code Général des Impôts — Droit de timbre communal copie certifiée";
                        String extraitOrgCode = selectedOrg != null ? selectedOrg.getCode() : communeOrgCode;
                        rawRules.add(new RawRule(extraitOrgCode, "Timbre communal & expédition d'état civil", new BigDecimal("100.00")));
                    }
                }
            }
            case "DEM-CASIER-JUD" -> {
                total = new BigDecimal("2500.00");
                refLegale = "Frais de greffe & timbre fiscal du Bulletin N°3 du Casier Judiciaire";
                rawRules.add(new RawRule(justiceOrgCode, "Émission & timbre du Greffe Central", new BigDecimal("80.00")));
                rawRules.add(new RawRule(policeOrgCode, "Consultation du fichier central des antécédents", new BigDecimal("20.00")));
            }
            default -> {
                total = new BigDecimal("2000.00");
                refLegale = "Tarif administratif standard CIVICA";
                String defaultOrgCode = selectedOrg != null ? selectedOrg.getCode() : "COMMUNE-YDE-1";
                rawRules.add(new RawRule(defaultOrgCode, "Frais administratifs d'instruction", new BigDecimal("100.00")));
            }
        }

        List<AllocationRuleDto> computedParts = new ArrayList<>();
        if (total.compareTo(BigDecimal.ZERO) > 0 && !rawRules.isEmpty()) {
            BigDecimal pctSum = rawRules.stream().map(RawRule::pct).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (pctSum.compareTo(new BigDecimal("100.00")) != 0) {
                throw BusinessException.internalServerError(
                        "Configuration de répartition invalide pour " + code + " (" + pctSum + "% au lieu de 100%)."
                );
            }

            // Résoudre chaque organisation et consolider d'éventuels doublons (sans perte de pourcentage)
            Map<Long, MergedRule> consolidated = new LinkedHashMap<>();
            for (RawRule r : rawRules) {
                Organisation org = resolveOrganisationForSplit(r.orgCode, targetService);
                consolidated.compute(org.getId(), (id, existing) -> {
                    if (existing == null) {
                        return new MergedRule(org, r.libelle, r.pct);
                    }
                    return new MergedRule(org, existing.libelle + " + " + r.libelle, existing.pct.add(r.pct));
                });
            }

            List<MergedRule> mergedList = new ArrayList<>(consolidated.values());
            BigDecimal sumSoFar = BigDecimal.ZERO;
            for (int i = 0; i < mergedList.size(); i++) {
                MergedRule r = mergedList.get(i);
                BigDecimal amount;
                if (i == mergedList.size() - 1) {
                    amount = total.subtract(sumSoFar).setScale(2, RoundingMode.HALF_UP);
                } else {
                    amount = total.multiply(r.pct)
                            .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP)
                            .setScale(2, RoundingMode.HALF_UP);
                    sumSoFar = sumSoFar.add(amount);
                }
                computedParts.add(AllocationRuleDto.builder()
                        .organisationCode(r.org.getCode())
                        .organisationNom(r.org.getNom())
                        .libelleQuotePart(r.libelle)
                        .pourcentage(r.pct)
                        .montantPrevu(amount)
                        .build());
            }
        }

        return BaremePaiementDto.builder()
                .demarcheCode(code)
                .demarcheTitre(titre)
                .varianteCircuit(variante)
                .montantTotal(total)
                .devise(DEVISE_DEFAUT)
                .gratuit(total.compareTo(BigDecimal.ZERO) == 0)
                .referenceLegale(refLegale)
                .repartitionPrevue(computedParts)
                .build();
    }

    private String defaultVarianteForDemarche(String code) {
        if ("DEM-ACTE-NAISS".equalsIgnoreCase(code)) {
            return "EXTRAIT_COPIE";
        }
        return "STANDARD";
    }

    private Organisation resolveOrganisationForSplit(String orgCode, ServiceAdministratif targetService) {
        return organisationRepository.findByCode(orgCode)
                .or(() -> {
                    if (targetService != null && targetService.getOrganisation() != null) {
                        return Optional.of(targetService.getOrganisation());
                    }
                    return organisationRepository.findAll().stream().findFirst();
                })
                .orElseThrow(() -> BusinessException.badRequest("Aucune organisation disponible pour l'allocation financière."));
    }

    private Organisation requireUserOrganisation(Utilisateur user) {
        Organisation org = user.resolveEffectiveOrganisation();
        if (org == null) {
            throw BusinessException.forbidden("Aucune organisation administrative n'est rattachée à votre compte.");
        }
        return org;
    }

    private Long resolveTargetWalletId(Utilisateur user, Long requestedWalletId) {
        if (user.getRole() == Role.ROLE_RESPONSABLE) {
            Organisation ownOrg = requireUserOrganisation(user);
            InstitutionWallet ownWallet = ensureWalletForOrganisation(ownOrg);
            if (requestedWalletId != null && !requestedWalletId.equals(ownWallet.getId())) {
                throw BusinessException.forbidden("Vous ne pouvez opérer que sur le portefeuille de votre organisation (" + ownOrg.getNom() + ").");
            }
            return ownWallet.getId();
        }
        if (requestedWalletId == null) {
            throw BusinessException.badRequest("Veuillez sélectionner le portefeuille institutionnel concerné.");
        }
        return requestedWalletId;
    }

    private void verifyWalletScope(Utilisateur user, Organisation walletOrg) {
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) {
            return;
        }
        if (user.getRole() == Role.ROLE_RESPONSABLE) {
            Organisation ownOrg = requireUserOrganisation(user);
            if (!ownOrg.getId().equals(walletOrg.getId())) {
                throw BusinessException.forbidden("Accès refusé : ce portefeuille appartient à " + walletOrg.getNom() + ".");
            }
            return;
        }
        throw BusinessException.forbidden("Action financière réservée aux responsables et administrateurs.");
    }

    private String normalizeModePaiement(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            throw BusinessException.badRequest(
                    "Le moyen de paiement est obligatoire (MOMO, OM, CARTE_BANCAIRE, COMPTE_BANCAIRE)."
            );
        }
        String normalized = rawMode.trim().toUpperCase();
        return switch (normalized) {
            case "MOMO", "OM", "CARTE_BANCAIRE", "COMPTE_BANCAIRE" -> normalized;
            default -> throw BusinessException.badRequest(
                    "Moyen de paiement non pris en charge : " + rawMode
                            + ". Moyens acceptés : MOMO, OM, CARTE_BANCAIRE, COMPTE_BANCAIRE."
            );
        };
    }

    private String maskInstrument(String mode, String raw) {
        String val = raw != null ? raw.replaceAll("\\s+", "") : "";
        return switch (mode) {
            case "MOMO", "MTN_MOMO" -> {
                String digits = val.replaceAll("[^0-9+]", "");
                if (digits.length() >= 4) {
                    yield "MOMO •••• " + digits.substring(digits.length() - 4);
                }
                yield "Mobile Money (MOMO)";
            }
            case "OM", "ORANGE_MONEY" -> {
                String digits = val.replaceAll("[^0-9+]", "");
                if (digits.length() >= 4) {
                    yield "OM •••• " + digits.substring(digits.length() - 4);
                }
                yield "Orange Money (OM)";
            }
            case "CARTE_BANCAIRE" -> {
                String digits = val.replaceAll("[^0-9]", "");
                if (digits.length() >= 4) {
                    yield "Carte •••• " + digits.substring(digits.length() - 4);
                }
                yield "Carte Bancaire";
            }
            case "COMPTE_BANCAIRE" -> {
                String accountChars = val.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
                if (accountChars.length() >= 4) {
                    yield "Compte •••• " + accountChars.substring(accountChars.length() - 4);
                }
                yield "Compte Bancaire (RIB/IBAN)";
            }
            default -> "Guichet Trésor Public";
        };
    }

    private String formatModeLabel(String mode) {
        if (mode == null) {
            return "Paiement CIVICA";
        }
        return switch (mode) {
            case "MOMO" -> "Mobile Money (MOMO)";
            case "OM" -> "Orange Money (OM)";
            case "CARTE_BANCAIRE" -> "Carte Bancaire";
            case "COMPTE_BANCAIRE" -> "Compte Bancaire";
            case "MTN_MOMO" -> "MTN Mobile Money";
            case "ORANGE_MONEY" -> "Orange Money";
            case "GUICHET_TRESOR" -> "Guichet Trésor";
            default -> mode;
        };
    }

    private String generateReference(String prefix) {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String randomPart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return prefix + "-" + datePart + "-" + randomPart;
    }

    private BigDecimal sumField(List<InstitutionWallet> wallets, java.util.function.Function<InstitutionWallet, BigDecimal> getter) {
        return wallets.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public PaiementDto toPaiementDto(Paiement p) {
        Dossier dossier = p.getDossier();
        String numeroSuivi = dossier != null ? dossier.getNumeroSuivi() : null;
        String demarcheCode = dossier != null && dossier.getDemarche() != null
                ? dossier.getDemarche().getCode()
                : p.getDemarcheCode();
        String demarcheTitre;
        if (dossier != null && dossier.getDemarche() != null) {
            demarcheTitre = dossier.getDemarche().getTitre();
        } else if (demarcheCode != null && demarcheRepository != null) {
            demarcheTitre = demarcheRepository.findByCode(demarcheCode)
                    .map(Demarche::getTitre)
                    .orElse(demarcheCode);
        } else {
            demarcheTitre = demarcheCode;
        }

        return PaiementDto.builder()
                .id(p.getId())
                .referenceTransaction(p.getReferenceTransaction())
                .quittanceNumero(p.getQuittanceNumero())
                .numeroSuivi(numeroSuivi)
                .demarcheCode(demarcheCode)
                .demarcheTitre(demarcheTitre)
                .serviceId(p.getServiceId())
                .citoyenNomComplet(p.getCitoyen().getPrenom() + " " + p.getCitoyen().getNom())
                .montantTotal(p.getMontantTotal())
                .devise(p.getDevise())
                .modePaiement(p.getModePaiement())
                .instrumentMasque(p.getInstrumentMasque())
                .varianteCircuit(p.getVarianteCircuit())
                .statut(p.getStatut())
                .createdAt(p.getCreatedAt())
                .confirmedAt(p.getConfirmedAt())
                .allocations(p.getAllocations().stream().map(this::toAllocationDto).collect(Collectors.toList()))
                .build();
    }

    public PaymentAllocationDto toAllocationDto(PaymentAllocation a) {
        String numeroSuivi = a.getPaiement().getDossier() != null
                ? a.getPaiement().getDossier().getNumeroSuivi()
                : null;
        return PaymentAllocationDto.builder()
                .id(a.getId())
                .paiementId(a.getPaiement().getId())
                .referenceTransaction(a.getPaiement().getReferenceTransaction())
                .numeroSuivi(numeroSuivi)
                .walletId(a.getWallet().getId())
                .organisationId(a.getOrganisation().getId())
                .organisationCode(a.getOrganisation().getCode())
                .organisationNom(a.getOrganisation().getNom())
                .libelleQuotePart(a.getLibelleQuotePart())
                .pourcentage(a.getPourcentage())
                .montantAlloue(a.getMontantAlloue())
                .devise(a.getWallet().getDevise())
                .createdAt(a.getCreatedAt())
                .build();
    }

    public InstitutionWalletDto toWalletDto(InstitutionWallet w) {
        List<PaymentAllocationDto> recent = allocationRepository.findTop30ByWalletIdOrderByCreatedAtDesc(w.getId())
                .stream()
                .limit(5)
                .map(this::toAllocationDto)
                .collect(Collectors.toList());

        return InstitutionWalletDto.builder()
                .id(w.getId())
                .organisationId(w.getOrganisation().getId())
                .organisationCode(w.getOrganisation().getCode())
                .organisationNom(w.getOrganisation().getNom())
                .typeOrganisation(w.getOrganisation().getTypeOrganisation())
                .numeroCompteTresor(w.getNumeroCompteTresor())
                .soldeDisponible(w.getSoldeDisponible())
                .soldeBloque(w.getSoldeBloque())
                .totalEncaisse(w.getTotalEncaisse())
                .totalDecaisse(w.getTotalDecaisse())
                .devise(w.getDevise())
                .updatedAt(w.getUpdatedAt())
                .allocationsRecentes(recent)
                .build();
    }

    public WithdrawalRequestDto toWithdrawalDto(WithdrawalRequest wr, Utilisateur viewer) {
        boolean isPending = "DEMANDE".equals(wr.getStatut());
        boolean sameUser = wr.getRequestedBy().getId().equals(viewer.getId());
        boolean canDecide = isPending && !sameUser &&
                (viewer.getRole() == Role.ROLE_SUPER_ADMIN || viewer.getRole() == Role.ROLE_RESPONSABLE);

        String blockReason = null;
        if (isPending && sameUser) {
            blockReason = "Double validation requise : vous êtes l'initiateur de cette demande. Un autre responsable ou contrôleur doit l'approuver.";
        }

        return WithdrawalRequestDto.builder()
                .id(wr.getId())
                .referenceDemande(wr.getReferenceDemande())
                .walletId(wr.getWallet().getId())
                .numeroCompteTresor(wr.getWallet().getNumeroCompteTresor())
                .organisationId(wr.getOrganisation().getId())
                .organisationCode(wr.getOrganisation().getCode())
                .organisationNom(wr.getOrganisation().getNom())
                .montant(wr.getMontant())
                .devise(wr.getWallet().getDevise())
                .compteBeneficiaire(wr.getCompteBeneficiaire())
                .motif(wr.getMotif())
                .statut(wr.getStatut())
                .requestedById(wr.getRequestedBy().getId())
                .requestedByNom(wr.getRequestedBy().getPrenom() + " " + wr.getRequestedBy().getNom())
                .requestedByEmail(wr.getRequestedBy().getEmail())
                .authorizedById(wr.getAuthorizedBy() != null ? wr.getAuthorizedBy().getId() : null)
                .authorizedByNom(wr.getAuthorizedBy() != null
                        ? wr.getAuthorizedBy().getPrenom() + " " + wr.getAuthorizedBy().getNom()
                        : null)
                .commentaireDecision(wr.getCommentaireDecision())
                .createdAt(wr.getCreatedAt())
                .decidedAt(wr.getDecidedAt())
                .canCurrentUserDecide(canDecide)
                .fourEyesBlockReason(blockReason)
                .build();
    }

    private record RawRule(String orgCode, String libelle, BigDecimal pct) {}
    private record MergedRule(Organisation org, String libelle, BigDecimal pct) {}
    private record ResolvedSplit(Long walletId, Organisation organisation, AllocationRuleDto rule) {}
}
