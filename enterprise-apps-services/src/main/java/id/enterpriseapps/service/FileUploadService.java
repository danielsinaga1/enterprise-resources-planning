package id.enterpriseapps.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FileUploadService {

    private final Path uploadDir;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_TYPES = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    public FileUploadService() {
        this.uploadDir = Paths.get(System.getProperty("user.dir"), "uploads").toAbsolutePath().normalize();
        log.info("Upload directory resolved to: {}", this.uploadDir);
    }

    public Path getUploadDir() {
        return uploadDir;
    }

    /**
     * Upload multiple files and return list of stored filenames.
     */
    public List<String> uploadFiles(MultipartFile[] files) throws IOException {
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        List<String> filenames = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            validateFile(file);
            String filename = storeFile(file);
            filenames.add(filename);
        }
        return filenames;
    }

    /**
     * Delete a file by filename.
     */
    public boolean deleteFile(String filename) {
        try {
            // Prevent path traversal
            Path filePath = uploadDir.resolve(filename).normalize();
            if (!filePath.startsWith(uploadDir)) {
                log.warn("Path traversal attempt: {}", filename);
                return false;
            }
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", filename, e);
            return false;
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File " + file.getOriginalFilename() + " exceeds maximum size of 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "File type not allowed. Allowed: JPEG, PNG, GIF, WEBP");
        }
    }

    private String storeFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String storedFilename = UUID.randomUUID().toString() + extension;

        Path targetPath = uploadDir.resolve(storedFilename).normalize();
        if (!targetPath.startsWith(uploadDir)) {
            throw new IOException("Invalid file path");
        }

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Stored file: {} -> {}", originalFilename, storedFilename);
        return storedFilename;
    }
}

