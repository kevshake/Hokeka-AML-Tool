package com.posgateway.aml.service.document;



import com.posgateway.aml.entity.merchant.MerchantDocument;
import com.posgateway.aml.repository.MerchantDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// @RequiredArgsConstructor removed
@Service
public class DocumentManagementService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DocumentManagementService.class);

    private final MerchantDocumentRepository documentRepository;

    public DocumentManagementService(MerchantDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }


    @Value("${app.document.upload-dir:./uploads}")
    private String uploadDir;

    public MerchantDocument uploadDocument(Long merchantId, MultipartFile file, String documentType)
            throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("File name cannot be null");
        }
        String originalFilename = StringUtils.cleanPath(filename);
        if (originalFilename.contains("..")) {
            throw new IllegalArgumentException("Invalid filename sequence " + originalFilename);
        }

        // Create directory if not exists
        Path merchantUploadPath = Paths.get(uploadDir).resolve(String.valueOf(merchantId));
        if (!Files.exists(merchantUploadPath)) {
            Files.createDirectories(merchantUploadPath);
        }

        // Generate unique filename
        String uniqueFileName = UUID.randomUUID().toString() + "_" + originalFilename;
        Path targetLocation = merchantUploadPath.resolve(uniqueFileName);

        // Save file
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        // Save metadata
        MerchantDocument doc = new MerchantDocument();
        doc.setMerchantId(merchantId);
        doc.setDocumentType(documentType);
        doc.setFileName(originalFilename);
        doc.setFilePath(targetLocation.toString());
        doc.setStatus("PENDING");

        return documentRepository.save(doc);
    }

    public MerchantDocument verifyDocument(Long documentId, boolean approved) {
        MerchantDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        doc.setStatus(approved ? "VERIFIED" : "REJECTED");
        doc.setVerifiedAt(LocalDateTime.now());

        log.info("Document {} verified: {}", documentId, doc.getStatus());
        return documentRepository.save(doc);
    }

    public List<MerchantDocument> getDocuments(Long merchantId) {
        return documentRepository.findByMerchantId(merchantId);
    }
}
