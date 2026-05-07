package com.posgateway.aml.controller.document;



import com.posgateway.aml.dto.response.MerchantDocumentDto;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.merchant.MerchantDocument;
import com.posgateway.aml.repository.MerchantDocumentRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.compliance.AuditService;
import com.posgateway.aml.service.document.DocumentManagementService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("")
public class DocumentController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DocumentController.class);

    private final DocumentManagementService documentService;
    private final MerchantDocumentRepository documentRepository;
    private final MerchantRepository merchantRepository;
    private final AuditService auditService;

    public DocumentController(DocumentManagementService documentService,
                              MerchantDocumentRepository documentRepository,
                              MerchantRepository merchantRepository,
                              AuditService auditService) {
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.merchantRepository = merchantRepository;
        this.auditService = auditService;
    }

    private com.posgateway.aml.entity.User getCurrentUser() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.posgateway.aml.entity.User) {
            return (com.posgateway.aml.entity.User) auth.getPrincipal();
        }
        return null;
    }


    @PostMapping("/merchants/{merchantId}/documents")
    public ResponseEntity<MerchantDocumentDto> uploadDocument(
            @PathVariable Long merchantId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type) throws IOException {

        MerchantDocument saved = documentService.uploadDocument(merchantId, file, type);
        return ResponseEntity.ok(MerchantDocumentDto.from(saved));
    }

    @GetMapping("/merchants/{merchantId}/documents")
    public ResponseEntity<List<MerchantDocumentDto>> getDocuments(@PathVariable Long merchantId) {
        List<MerchantDocumentDto> dtos = documentService.getDocuments(merchantId).stream()
                .map(MerchantDocumentDto::from)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/documents/{documentId}/verify")
    public ResponseEntity<MerchantDocumentDto> verifyDocument(
            @PathVariable Long documentId,
            @RequestParam boolean approved) {

        MerchantDocument verified = documentService.verifyDocument(documentId, approved);
        return ResponseEntity.ok(MerchantDocumentDto.from(verified));
    }

    /**
     * Stream a document inline (suitable for in-browser preview).
     * GET /documents/{id}/file
     */
    @GetMapping("/documents/{documentId}/file")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'COMPLIANCE_OFFICER', 'SCREENING_ANALYST', 'PSP_ADMIN', 'PSP_ANALYST', 'PSP_USER', 'VIEWER', 'INVESTIGATOR', 'CASE_MANAGER', 'AUDITOR')")
    public ResponseEntity<InputStreamResource> streamDocument(@PathVariable Long documentId) throws IOException {
        return serveDocument(documentId, false);
    }

    /**
     * Force-download a document as an attachment.
     * GET /documents/{id}/download
     */
    @GetMapping("/documents/{documentId}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'COMPLIANCE_OFFICER', 'SCREENING_ANALYST', 'PSP_ADMIN', 'PSP_ANALYST', 'PSP_USER', 'VIEWER', 'INVESTIGATOR', 'CASE_MANAGER', 'AUDITOR')")
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable Long documentId) throws IOException {
        return serveDocument(documentId, true);
    }

    private ResponseEntity<InputStreamResource> serveDocument(Long documentId, boolean attachment) throws IOException {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        MerchantDocument doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }

        // PSP-scoped access check: a PSP user must own (via Merchant.psp) the
        // document's merchant. Users without a PSP (e.g. SUPER_ADMIN) are
        // treated as global readers.
        if (user.getPsp() != null) {
            Merchant merchant = merchantRepository.findById(doc.getMerchantId()).orElse(null);
            if (merchant == null
                    || merchant.getPsp() == null
                    || !merchant.getPsp().getPspId().equals(user.getPsp().getPspId())) {
                return ResponseEntity.status(403).build();
            }
        }

        Path path = Paths.get(doc.getFilePath());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            log.warn("Document {} file missing on disk at {}", documentId, doc.getFilePath());
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = MediaTypeFactory.getMediaType(doc.getFileName())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        ContentDisposition disposition = (attachment
                ? ContentDisposition.attachment()
                : ContentDisposition.inline())
                .filename(doc.getFileName())
                .build();

        long contentLength = Files.size(path);
        InputStream in = Files.newInputStream(path);
        InputStreamResource resource = new InputStreamResource(in);

        // Audit: record a download/view event. Async + REQUIRES_NEW so the
        // stream still serves cleanly even if the audit write fails.
        try {
            auditService.logAction(
                    attachment ? "DOCUMENT_DOWNLOADED" : "DOCUMENT_VIEWED",
                    doc.getMerchantId(),
                    user.getUsername(),
                    "documentId=" + documentId + " fileName=" + doc.getFileName(),
                    java.util.Map.of(
                            "documentId", documentId,
                            "fileName", doc.getFileName(),
                            "documentType", doc.getDocumentType() == null ? "" : doc.getDocumentType(),
                            "disposition", attachment ? "attachment" : "inline"
                    )
            );
        } catch (Exception ex) {
            log.warn("Audit logging failed for document {} access: {}", documentId, ex.getMessage());
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(contentLength)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }
}
