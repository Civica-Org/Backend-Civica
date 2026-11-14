package cm.civica.backend.service;

import cm.civica.backend.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${civica.upload.dir:uploads}")
    private String uploadDir;

    private Path rootLocation;

    @PostConstruct
    public void init() {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            throw BusinessException.internalServerError("Impossible d'initialiser le répertoire de stockage des pièces : " + e.getMessage());
        }
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("Le fichier téléversé est vide.");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String storedFilename = UUID.randomUUID() + extension;

        try {
            Path targetLocation = this.rootLocation.resolve(storedFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return storedFilename;
        } catch (IOException e) {
            throw BusinessException.internalServerError("Échec du stockage de la pièce justificative : " + e.getMessage());
        }
    }

    public Path load(String filename) {
        return this.rootLocation.resolve(filename);
    }
}

