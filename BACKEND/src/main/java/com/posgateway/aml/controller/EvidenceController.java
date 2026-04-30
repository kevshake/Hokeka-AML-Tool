package com.posgateway.aml.controller;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.CaseEvidence;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.repository.compliance.CaseEvidenceRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.case_management.EvidenceStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/cases")
public class EvidenceController {

    private final EvidenceStorageService evidenceStorageService;
    private final CaseEvidenceRepository caseEvidenceRepository;
    private final ComplianceCaseRepository complianceCaseRepository;
    private final UserRepository userRepository;

    @Autowired
    public EvidenceController(EvidenceStorageService evidenceStorageService,
            CaseEvidenceRepository caseEvidenceRepository,
            ComplianceCaseRepository complianceCaseRepository,
            UserRepository userRepository) {
        this.evidenceStorageService = evidenceStorageService;
        this.caseEvidenceRepository = caseEvidenceRepository;
        this.complianceCaseRepository = complianceCaseRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/{caseId}/evidence")
    public ResponseEntity<CaseEvidence> uploadEvidence(@PathVariable Long caseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("description") String description,
            @RequestParam("uploadedBy") Long uploadedBy) { // Simplified User ID
        try {
            ComplianceCase cCase = complianceCaseRepository.findById(caseId)
                    .orElseThrow(() -> new IllegalArgumentException("Case not found"));

            // Store file physically
            EvidenceStorageService.StoredFile storedFile = evidenceStorageService.storeEvidence(file);

            // Create DB record
            CaseEvidence evidence = new CaseEvidence();
            evidence.setComplianceCase(cCase);
            evidence.setFileName(storedFile.getOriginalFilename());
            evidence.setStoragePath(storedFile.getStoredFilename()); // Storing filename as relative path
            evidence.setFileType(extractFileType(file.getContentType(), storedFile.getOriginalFilename()));
            // Get User entity from uploadedBy ID
            User user = userRepository.findById(uploadedBy)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            evidence.setUploadedBy(user);
            evidence.setDescription(description);
            evidence.setUploadedAt(LocalDateTime.now());

            CaseEvidence savedEvidence = caseEvidenceRepository.save(evidence);
            return ResponseEntity.ok(savedEvidence);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{caseId}/evidence")
    public ResponseEntity<List<CaseEvidence>> listEvidence(@PathVariable Long caseId) {
        List<CaseEvidence> evidenceList = caseEvidenceRepository.findByComplianceCase_Id(caseId);
        return ResponseEntity.ok(evidenceList);
    }

    @GetMapping("/{caseId}/evidence/{evidenceId}/download")
    public ResponseEntity<Resource> downloadEvidence(@PathVariable Long caseId, @PathVariable Long evidenceId) {
        CaseEvidence evidence = caseEvidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("Evidence not found"));

        if (!evidence.getComplianceCase().getId().equals(caseId)) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Path filePath = evidenceStorageService.loadEvidence(evidence.getStoragePath());
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + evidence.getFileName() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String extractFileType(String contentType, String filename) {
        if (contentType != null && !contentType.equals("application/octet-stream")) {
            return contentType;
        }
        if (filename.contains(".")) {
            return filename.substring(filename.lastIndexOf(".") + 1).toUpperCase();
        }
        return "UNKNOWN";
    }
}
