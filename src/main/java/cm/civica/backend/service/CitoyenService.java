package cm.civica.backend.service;

import cm.civica.backend.dto.*;
import cm.civica.backend.enums.StatutDossier;
import cm.civica.backend.enums.StatutPiece;
import cm.civica.backend.exception.BusinessException;
import cm.civica.backend.model.*;
import cm.civica.backend.pattern.observer.DossierEvent;
import cm.civica.backend.pattern.observer.DossierSubject;
import cm.civica.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CitoyenService {

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private DemarcheRepository demarcheRepository;

    @Autowired
    private ServiceAdministratifRepository serviceRepository;

    @Autowired
    private PieceJustificativeRepository pieceRepository;

    @Autowired
    private HistoriqueDossierRepository historiqueRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private EmailService emailService;

    // Pattern GoF : Observer
    @Autowired
    private DossierSubject dossierSubject;

    @Transactional
    public DossierResponseDto deposerDossier(DepotDossierRequest request) {
        Utilisateur citoyen = authService.getCurrentUser();

        Demarche demarche = demarcheRepository.findByCode(request.getDemarcheCode())
                .orElseThrow(() -> BusinessException.notFound("Démarche introuvable avec le code : " + request.getDemarcheCode()));

        ServiceAdministratif serviceCourant = null;
        if (request.getServiceId() != null) {
            serviceCourant = serviceRepository.findById(request.getServiceId())
                    .orElseThrow(() -> BusinessException.notFound("Service administratif introuvable"));
        } else {
            // Service par défaut si non spécifié
            List<ServiceAdministratif> services = serviceRepository.findByActifTrue();
            if (!services.isEmpty()) {
                serviceCourant = services.get(0);
            }
        }

        String numeroSuivi = genererNumeroSuiviUnique();

        Dossier dossier = Dossier.builder()
                .numeroSuivi(numeroSuivi)
                .citoyen(citoyen)
                .demarche(demarche)
                .serviceCourant(serviceCourant)
                .statut(StatutDossier.SOUMIS)
                .priorite(request.getPriorite() != null ? request.getPriorite() : "NORMALE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Dossier savedDossier = dossierRepository.save(dossier);

        // Notification découplée via le Pattern Observer (Audit, Email de confirmation, Télémétrie)
        dossierSubject.notifyObservers(DossierEvent.of(
                savedDossier, null, StatutDossier.SOUMIS,
                "DEPOT_DOSSIER",
                "Dépôt initial du dossier par le citoyen " + citoyen.getPrenom() + " " + citoyen.getNom(),
                citoyen, serviceCourant
        ));

        return toDto(savedDossier);
    }

    @Transactional
    public PieceJustificativeDto ajouterPiece(String numeroSuivi, String intitule, MultipartFile file) {
        Utilisateur user = authService.getCurrentUser();
        Dossier dossier = dossierRepository.findByNumeroSuivi(numeroSuivi)
                .orElseThrow(() -> BusinessException.notFound("Dossier introuvable avec le numéro : " + numeroSuivi));

        // Vérification des droits : le citoyen propriétaire ou un agent/admin
        boolean isOwner = dossier.getCitoyen().getId().equals(user.getId());
        boolean isStaff = user.getRole().name().contains("AGENT") || user.getRole().name().contains("RESPONSABLE") || user.getRole().name().contains("ADMIN");
        if (!isOwner && !isStaff) {
            throw BusinessException.forbidden("Vous n'êtes pas autorisé à ajouter des pièces sur ce dossier.");
        }

        String storedFilename = fileStorageService.store(file);

        PieceJustificative piece = PieceJustificative.builder()
                .dossier(dossier)
                .intitule(intitule != null && !intitule.isBlank() ? intitule : file.getOriginalFilename())
                .typeMime(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .nomFichierStocke(storedFilename)
                .tailleOctets(file.getSize())
                .statutValidation(StatutPiece.EN_ATTENTE)
                .uploadedAt(Instant.now())
                .build();

        PieceJustificative savedPiece = pieceRepository.save(piece);

        return toPieceDto(savedPiece);
    }

    @Transactional(readOnly = true)
    public DossierResponseDto getDossierByNumeroSuivi(String numeroSuivi) {
        Dossier dossier = dossierRepository.findByNumeroSuiviWithPiecesAndDemarche(numeroSuivi)
                .orElseThrow(() -> BusinessException.notFound("Aucun dossier trouvé avec le numéro de suivi : " + numeroSuivi));

        return toDto(dossier);
    }

    @Transactional(readOnly = true)
    public List<DossierResponseDto> getMesDossiers() {
        Utilisateur citoyen = authService.getCurrentUser();
        return dossierRepository.findByCitoyenIdOrderByCreatedAtDesc(citoyen.getId()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private String genererNumeroSuiviUnique() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String prefix = String.format("CIVICA-%s-DOS-", dateStr);
        long countDuJour = dossierRepository.countByNumeroSuiviStartingWith(prefix);
        long sequence = countDuJour + 1;
        String numeroSuivi;
        do {
            numeroSuivi = String.format("%s%03d", prefix, sequence++);
        } while (dossierRepository.existsByNumeroSuivi(numeroSuivi));
        return numeroSuivi;
    }

    public DossierResponseDto toDto(Dossier dossier) {
        List<PieceJustificativeDto> piecesDto = pieceRepository.findByDossierId(dossier.getId()).stream()
                .map(this::toPieceDto)
                .collect(Collectors.toList());

        List<HistoriqueDossierDto> historiquesDto = historiqueRepository.findByDossierIdOrderByTimestampDesc(dossier.getId()).stream()
                .map(this::toHistoriqueDto)
                .collect(Collectors.toList());

        String orgNom = dossier.getServiceCourant() != null && dossier.getServiceCourant().getOrganisation() != null
                ? dossier.getServiceCourant().getOrganisation().getNom() : null;

        return DossierResponseDto.builder()
                .id(dossier.getId())
                .numeroSuivi(dossier.getNumeroSuivi())
                .citoyenId(dossier.getCitoyen().getId())
                .citoyenNomComplet(dossier.getCitoyen().getPrenom() + " " + dossier.getCitoyen().getNom())
                .citoyenEmail(dossier.getCitoyen().getEmail())
                .citoyenTelephone(dossier.getCitoyen().getTelephone())
                .demarcheCode(dossier.getDemarche().getCode())
                .demarcheTitre(dossier.getDemarche().getTitre())
                .delaiIndicatifJours(dossier.getDemarche().getDelaiIndicatifJours())
                .serviceCourantId(dossier.getServiceCourant() != null ? dossier.getServiceCourant().getId() : null)
                .serviceCourantNom(dossier.getServiceCourant() != null ? dossier.getServiceCourant().getNom() : null)
                .organisationNom(orgNom)
                .statut(dossier.getStatut())
                .priorite(dossier.getPriorite())
                .motifRejet(dossier.getMotifRejet())
                .createdAt(dossier.getCreatedAt())
                .updatedAt(dossier.getUpdatedAt())
                .pieces(piecesDto)
                .historiques(historiquesDto)
                .build();
    }

    public PieceJustificativeDto toPieceDto(PieceJustificative piece) {
        return PieceJustificativeDto.builder()
                .id(piece.getId())
                .intitule(piece.getIntitule())
                .typeMime(piece.getTypeMime())
                .nomFichierStocke(piece.getNomFichierStocke())
                .tailleOctets(piece.getTailleOctets())
                .statutValidation(piece.getStatutValidation())
                .observationsIa(piece.getObservationsIa())
                .urlTelechargement("/uploads/" + piece.getNomFichierStocke())
                .uploadedAt(piece.getUploadedAt())
                .build();
    }

    public HistoriqueDossierDto toHistoriqueDto(HistoriqueDossier h) {
        String agentNom = h.getAgent() != null ? h.getAgent().getPrenom() + " " + h.getAgent().getNom() : "Système";
        String srcNom = h.getServiceSource() != null ? h.getServiceSource().getNom() : null;
        String cibleNom = h.getServiceCible() != null ? h.getServiceCible().getNom() : null;

        return HistoriqueDossierDto.builder()
                .id(h.getId())
                .numeroSuivi(h.getDossier() != null ? h.getDossier().getNumeroSuivi() : null)
                .agentNom(agentNom)
                .serviceSourceNom(srcNom)
                .serviceCibleNom(cibleNom)
                .statutPrecedent(h.getStatutPrecedent())
                .nouveauStatut(h.getNouveauStatut())
                .actionEffectuee(h.getActionEffectuee())
                .commentaire(h.getCommentaire())
                .timestamp(h.getTimestamp())
                .build();
    }
}

