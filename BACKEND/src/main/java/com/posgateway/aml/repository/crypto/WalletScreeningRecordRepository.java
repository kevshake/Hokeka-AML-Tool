package com.posgateway.aml.repository.crypto;

import com.posgateway.aml.entity.crypto.WalletScreeningRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface WalletScreeningRecordRepository extends JpaRepository<WalletScreeningRecord, Long> {
    Page<WalletScreeningRecord> findByPspIdOrderByScreenedAtDesc(Long pspId, Pageable pageable);
    Page<WalletScreeningRecord> findByWalletProfileIdAndPspIdOrderByScreenedAtDesc(
            Long walletProfileId, Long pspId, Pageable pageable);
    Page<WalletScreeningRecord> findByPspIdAndScreenedAtBetweenOrderByScreenedAtDesc(
            Long pspId, LocalDateTime from, LocalDateTime to, Pageable pageable);
}
