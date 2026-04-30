package com.posgateway.aml.controller.document;



import com.posgateway.aml.entity.merchant.MerchantDocument;
import com.posgateway.aml.service.document.DocumentManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

// @RequiredArgsConstructor removed
@RestController
@RequestMapping("")
public class DocumentController {

    private final DocumentManagementService documentService;

    public DocumentController(DocumentManagementService documentService) {
        this.documentService = documentService;
    }


    @PostMapping("/merchants/{merchantId}/documents")
    public ResponseEntity<MerchantDocument> uploadDocument(
            @PathVariable Long merchantId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type) throws IOException {

        return ResponseEntity.ok(documentService.uploadDocument(merchantId, file, type));
    }

    @GetMapping("/merchants/{merchantId}/documents")
    public ResponseEntity<List<MerchantDocument>> getDocuments(@PathVariable Long merchantId) {
        return ResponseEntity.ok(documentService.getDocuments(merchantId));
    }

    @PutMapping("/documents/{documentId}/verify")
    public ResponseEntity<MerchantDocument> verifyDocument(
            @PathVariable Long documentId,
            @RequestParam boolean approved) {

        return ResponseEntity.ok(documentService.verifyDocument(documentId, approved));
    }
}
