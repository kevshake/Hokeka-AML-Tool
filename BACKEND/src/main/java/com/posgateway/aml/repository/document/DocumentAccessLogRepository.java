package com.posgateway.aml.repository.document;

import com.posgateway.aml.entity.document.DocumentAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentAccessLogRepository extends JpaRepository<DocumentAccessLog, Long> {
    List<DocumentAccessLog> findByDocumentIdOrderByAccessedAtDesc(Long documentId);
    List<DocumentAccessLog> findByUserIdOrderByAccessedAtDesc(Long userId);
}

