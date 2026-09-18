package com.posgateway.aml.service.document;

import com.posgateway.aml.entity.merchant.MerchantDocument;
import com.posgateway.aml.repository.MerchantDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Document Search Service
 * Enhanced search functionality for documents
 */
@Service
public class DocumentSearchService {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(DocumentSearchService.class);

    private final MerchantDocumentRepository documentRepository;

    @Autowired
    public DocumentSearchService(MerchantDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Search documents by merchant ID and type
     */
    public List<MerchantDocument> searchByMerchantAndType(Long merchantId, String documentType) {
        return documentRepository.findByMerchantId(merchantId).stream()
                .filter(doc -> documentType == null || documentType.equals(doc.getDocumentType()))
                .collect(Collectors.toList());
    }

    /**
     * Search documents by status
     */
    public List<MerchantDocument> searchByStatus(String status) {
        return documentRepository.findAll().stream()
                .filter(doc -> status.equals(doc.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Search documents by file name pattern
     */
    public List<MerchantDocument> searchByFileName(String fileNamePattern) {
        return documentRepository.findAll().stream()
                .filter(doc -> doc.getFileName() != null && 
                        doc.getFileName().toLowerCase().contains(fileNamePattern.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Advanced search with multiple criteria
     */
    public List<MerchantDocument> advancedSearch(Long merchantId, String documentType, 
                                                  String status, String fileNamePattern) {
        return documentRepository.findByMerchantId(merchantId).stream()
                .filter(doc -> documentType == null || documentType.equals(doc.getDocumentType()))
                .filter(doc -> status == null || status.equals(doc.getStatus()))
                .filter(doc -> fileNamePattern == null || 
                        (doc.getFileName() != null && 
                         doc.getFileName().toLowerCase().contains(fileNamePattern.toLowerCase())))
                .collect(Collectors.toList());
    }
}

