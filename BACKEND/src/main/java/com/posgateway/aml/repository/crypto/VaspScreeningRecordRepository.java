package com.posgateway.aml.repository.crypto;

import com.posgateway.aml.entity.crypto.VaspScreeningRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaspScreeningRecordRepository extends JpaRepository<VaspScreeningRecord, Long> {
    Page<VaspScreeningRecord> findByPspIdOrderByScreenedAtDesc(Long pspId, Pageable pageable);
    Page<VaspScreeningRecord> findByVaspIdAndPspIdOrderByScreenedAtDesc(Long vaspId, Long pspId, Pageable pageable);
}
