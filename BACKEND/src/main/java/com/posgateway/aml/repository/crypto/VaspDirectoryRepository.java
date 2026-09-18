package com.posgateway.aml.repository.crypto;

import com.posgateway.aml.entity.crypto.VaspDirectoryEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

public interface VaspDirectoryRepository extends JpaRepository<VaspDirectoryEntry, Long> {
    Optional<VaspDirectoryEntry> findByIdAndPspId(Long id, Long pspId);
    Page<VaspDirectoryEntry> findByPspIdOrderByRiskScoreDescLegalNameAsc(Long pspId, Pageable pageable);
    Page<VaspDirectoryEntry> findByPspIdAndLegalNameContainingIgnoreCaseOrderByRiskScoreDesc(
            Long pspId, String legalName, Pageable pageable);
    List<VaspDirectoryEntry> findTop100BySanctionsNextScreeningAtLessThanEqualOrderBySanctionsNextScreeningAtAsc(
            LocalDateTime dueAt);
}
