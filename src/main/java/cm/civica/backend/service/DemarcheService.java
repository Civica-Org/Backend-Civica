package cm.civica.backend.service;

import cm.civica.backend.dto.DemarcheDto;
import cm.civica.backend.exception.BusinessException;
import cm.civica.backend.model.Demarche;
import cm.civica.backend.repository.DemarcheRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DemarcheService {

    @Autowired
    private DemarcheRepository demarcheRepository;

    @Transactional(readOnly = true)
    public List<DemarcheDto> getAllDemarchesActives() {
        return demarcheRepository.findByActifTrue().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DemarcheDto getDemarcheByCode(String code) {
        Demarche demarche = demarcheRepository.findByCode(code)
                .orElseThrow(() -> BusinessException.notFound("Démarche introuvable avec le code : " + code));
        return toDto(demarche);
    }

    public DemarcheDto toDto(Demarche demarche) {
        return DemarcheDto.builder()
                .id(demarche.getId())
                .code(demarche.getCode())
                .titre(demarche.getTitre())
                .description(demarche.getDescription())
                .delaiIndicatifJours(demarche.getDelaiIndicatifJours())
                .actif(demarche.getActif())
                .build();
    }
}

