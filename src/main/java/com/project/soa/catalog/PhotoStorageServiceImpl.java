package com.project.soa.catalog;

import com.project.soa.common.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Service
public class PhotoStorageServiceImpl implements PhotoStorageService {

    private static final String PHOTO_URL_PREFIX = "/uploads/photos/";
    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );

    private final Path storageDirectory;
    private final long maxSizeBytes;

    public PhotoStorageServiceImpl(
            @Value("${app.upload.photo-dir:uploads/photos}") String photoDir,
            @Value("${app.upload.photo-max-size-bytes:5242880}") long maxSizeBytes) {
        this.storageDirectory = Paths.get(photoDir).toAbsolutePath().normalize();
        this.maxSizeBytes = maxSizeBytes;
    }

    @Override
    public String store(MultipartFile file) {
        validate(file);

        String extension = ALLOWED_IMAGE_TYPES.get(file.getContentType());
        String filename = UUID.randomUUID() + extension;
        Path target = storageDirectory.resolve(filename).normalize();

        if (!target.startsWith(storageDirectory)) {
            throw new BusinessRuleException("Invalid photo storage path.");
        }

        try {
            Files.createDirectories(storageDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BusinessRuleException("Could not store uploaded photo.");
        }

        return PHOTO_URL_PREFIX + filename;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Photo file is required.");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new BusinessRuleException("Photo file exceeds the maximum allowed size.");
        }
        if (!ALLOWED_IMAGE_TYPES.containsKey(file.getContentType())) {
            throw new BusinessRuleException("Only JPEG, PNG, WEBP, and GIF images are allowed.");
        }
    }
}
