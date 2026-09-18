package com.posgateway.aml.repository;

import com.posgateway.aml.entity.merchant.MerchantDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MerchantDocumentRepository extends JpaRepository<MerchantDocument, Long> {
    List<MerchantDocument> findByMerchantId(Long merchantId);

    @org.springframework.data.jpa.repository.Query("SELECT d FROM MerchantDocument d WHERE d.expiryDate <= :thresholdDate AND d.status = 'VERIFIED' AND d.isCurrentVersion = true")
    List<MerchantDocument> findExpiringDocuments(java.time.LocalDate thresholdDate);

    List<MerchantDocument> findByExpiryDateBetween(java.time.LocalDate startDate, java.time.LocalDate endDate);

    List<MerchantDocument> findByExpiryDateBefore(java.time.LocalDate date);

    List<MerchantDocument> findByMerchantIdAndDocumentTypeOrderByVersionAsc(Long merchantId, String documentType);

    List<MerchantDocument> findByUploadedAtBeforeAndStatusAndIsCurrentVersion(java.time.LocalDateTime uploadedAt, String status, Boolean isCurrentVersion);
}
