package com.posgateway.aml.service.case_management;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Service for handling evidence file storage.
 * - Stores files locally (can be swapped for S3).
 * - Computes SHA-256 hash for integrity verification.
 * - Enforces immutable storage (conceptually).
 */
@Service
public class EvidenceStorageService {

    private static final Logger logger = LoggerFactory.getLogger(EvidenceStorageService.class);

    private final Path storageLocation;

    public EvidenceStorageService(@Value("${evidence.storage.path:evidence_store}") String storagePath) {
        this.storageLocation = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize evidence storage location", e);
        }
    }

    /**
     * Store a file and return its metadata including hash.
     */
    public StoredFile storeEvidence(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";

        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // Generate comprehensive unique ID
        String fileId = UUID.randomUUID().toString();
        String storedFilename = fileId + fileExtension;
        Path targetLocation = this.storageLocation.resolve(storedFilename);

        // Compute Hash while reading stream
        String sha256Hash;
        try (InputStream inputStream = file.getInputStream()) {
            // Note: In a real high-performance scenario, we'd digest and copy in one pass.
            // For simplicity here, we rely on MultipartFile capability or read twice if
            // needed,
            // but DigestUtils reads the stream.
            // IMPORTANT: MultipartFile stream might not support reset. Let's copy first,
            // then hash the file.
            Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
        }

        // Compute hash of stored file
        try (InputStream fileStream = Files.newInputStream(targetLocation)) {
            sha256Hash = DigestUtils.sha256Hex(fileStream);
        }

        logger.info("Stored evidence file {} as {} (Hash: {})", originalFilename, storedFilename, sha256Hash);

        return new StoredFile(fileId, storedFilename, sha256Hash, file.getSize(), originalFilename);
    }

    /**
     * Load a file as a resource.
     */
    public Path loadEvidence(String storedFilename) {
        return this.storageLocation.resolve(storedFilename).normalize();
    }

    public static class StoredFile {
        private final String fileId;
        private final String storedFilename;
        private final String sha256Hash;
        private final long size;
        private final String originalFilename;

        public StoredFile(String fileId, String storedFilename, String sha256Hash, long size, String originalFilename) {
            this.fileId = fileId;
            this.storedFilename = storedFilename;
            this.sha256Hash = sha256Hash;
            this.size = size;
            this.originalFilename = originalFilename;
        }

        public String getFileId() {
            return fileId;
        }

        public String getStoredFilename() {
            return storedFilename;
        }

        public String getSha256Hash() {
            return sha256Hash;
        }

        public long getSize() {
            return size;
        }

        public String getOriginalFilename() {
            return originalFilename;
        }
    }
}
