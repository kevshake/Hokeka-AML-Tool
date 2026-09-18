package com.posgateway.aml.service.document;

import com.posgateway.aml.entity.merchant.MerchantDocument;
import com.posgateway.aml.repository.MerchantDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentManagementService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DocumentManagementService.class);
    private static final Set<String> DOCUMENT_TYPES = Set.of(
            "ID_DOCUMENT", "PROOF_OF_ADDRESS", "BUSINESS_REGISTRATION", "BANK_STATEMENT",
            "PASSPORT", "LICENSE", "UTILITY_BILL", "OTHER");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "application/pdf", ".pdf",
            "image/jpeg", ".jpg",
            "image/png", ".png");

    private final MerchantDocumentRepository documentRepository;
    private final ClamAvDocumentScanner malwareScanner;
    private final Path uploadRoot;
    private final long maxFileSize;

    public DocumentManagementService(
            MerchantDocumentRepository documentRepository,
            ClamAvDocumentScanner malwareScanner,
            @Value("${app.document.upload-dir:./uploads}") String uploadDir,
            @Value("${app.document.max-size-bytes:10485760}") long maxFileSize) {
        this.documentRepository = documentRepository;
        this.malwareScanner = malwareScanner;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
    }

    public MerchantDocument uploadDocument(Long merchantId, MultipartFile file, String documentType,
                                           LocalDate expiryDate) throws IOException {
        if (merchantId == null) {
            throw new IllegalArgumentException("Merchant ID is required");
        }
        String normalizedType = normalizeDocumentType(documentType);
        String originalFilename = validateFilename(file.getOriginalFilename());
        byte[] content = validateAndRead(file);
        String detectedContentType = detectContentType(content);
        validateDeclaredContentType(file.getContentType(), detectedContentType);

        ClamAvDocumentScanner.ScanOutcome scan = malwareScanner.scan(content);
        if ("INFECTED".equals(scan.status())) {
            log.warn("Rejected infected KYC document for merchant {} (signature={})", merchantId, scan.threatName());
            throw new IllegalArgumentException("Document contains malware and was rejected");
        }

        Path merchantUploadPath = uploadRoot.resolve(String.valueOf(merchantId)).normalize();
        if (!merchantUploadPath.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid merchant document path");
        }
        Files.createDirectories(merchantUploadPath);
        Path target = merchantUploadPath.resolve(UUID.randomUUID() + EXTENSIONS.get(detectedContentType)).normalize();
        if (!target.startsWith(merchantUploadPath)) {
            throw new IllegalArgumentException("Invalid document path");
        }

        Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            MerchantDocument doc = new MerchantDocument();
            doc.setMerchantId(merchantId);
            doc.setDocumentType(normalizedType);
            doc.setFileName(originalFilename);
            doc.setFilePath(target.toString());
            doc.setContentType(detectedContentType);
            doc.setFileSize((long) content.length);
            doc.setSha256Hash(sha256(content));
            doc.setExpiryDate(expiryDate);
            doc.setStatus("PENDING");
            doc.setMalwareScanStatus(scan.status());
            doc.setMalwareScanEngine(scan.engine());
            doc.setMalwareThreatName(scan.threatName());
            doc.setMalwareScannedAt(scan.scannedAt());
            return documentRepository.save(doc);
        } catch (RuntimeException ex) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupError) {
                ex.addSuppressed(cleanupError);
            }
            throw ex;
        }
    }

    public MerchantDocument verifyDocument(Long documentId, boolean approved, String reviewer, String notes) {
        MerchantDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        if (!approved && (notes == null || notes.isBlank())) {
            throw new IllegalArgumentException("A rejection reason is required");
        }
        if ("INFECTED".equals(doc.getMalwareScanStatus())) {
            throw new IllegalArgumentException("An infected document cannot be approved");
        }

        doc.setStatus(approved ? "VERIFIED" : "REJECTED");
        doc.setVerifiedAt(LocalDateTime.now());
        doc.setVerifiedBy(reviewer);
        doc.setVerificationNotes(StringUtils.hasText(notes) ? notes.strip() : null);
        doc.setVerificationMethod("MANUAL_REVIEW");

        log.info("Document {} reviewed by {}: {}", documentId, reviewer, doc.getStatus());
        return documentRepository.save(doc);
    }

    public List<MerchantDocument> getDocuments(Long merchantId) {
        return documentRepository.findByMerchantId(merchantId);
    }

    private byte[] validateAndRead(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Document file cannot be empty");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("Document exceeds the maximum size of " + maxFileSize + " bytes");
        }
        byte[] content = file.getBytes();
        if (content.length == 0 || content.length > maxFileSize) {
            throw new IllegalArgumentException("Document size is invalid");
        }
        return content;
    }

    private String normalizeDocumentType(String documentType) {
        if (!StringUtils.hasText(documentType)) {
            throw new IllegalArgumentException("Document type is required");
        }
        String normalized = documentType.strip().toUpperCase(Locale.ROOT);
        if (!DOCUMENT_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported document type: " + documentType);
        }
        return normalized;
    }

    private String validateFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("File name cannot be empty");
        }
        String clean = StringUtils.cleanPath(filename).strip();
        if (clean.contains("..") || clean.contains("/") || clean.contains("\\") || clean.length() > 255) {
            throw new IllegalArgumentException("Invalid file name");
        }
        return clean;
    }

    private String detectContentType(byte[] content) {
        if (startsWith(content, new byte[]{0x25, 0x50, 0x44, 0x46, 0x2d})) {
            return "application/pdf";
        }
        if (startsWith(content, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
            return "image/jpeg";
        }
        if (startsWith(content, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
            return "image/png";
        }
        throw new IllegalArgumentException("Unsupported document content; only PDF, JPEG, and PNG are accepted");
    }

    private void validateDeclaredContentType(String declared, String detected) {
        if (!StringUtils.hasText(declared) || "application/octet-stream".equalsIgnoreCase(declared)) {
            return;
        }
        String normalized = declared.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(normalized)) {
            normalized = "image/jpeg";
        }
        if (!detected.equals(normalized)) {
            throw new IllegalArgumentException("Declared file type does not match document content");
        }
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) return false;
        }
        return true;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
