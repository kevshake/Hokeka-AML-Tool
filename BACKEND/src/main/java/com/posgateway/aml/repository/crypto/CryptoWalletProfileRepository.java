package com.posgateway.aml.repository.crypto;

import com.posgateway.aml.entity.crypto.CryptoWalletProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CryptoWalletProfileRepository extends JpaRepository<CryptoWalletProfile, Long> {
    Optional<CryptoWalletProfile> findByIdAndPspId(Long id, Long pspId);
    Optional<CryptoWalletProfile> findByPspIdAndAssetAccountId(Long pspId, Long assetAccountId);
    Optional<CryptoWalletProfile> findByPspIdAndNetworkAndWalletAddress(Long pspId, String network, String walletAddress);
    Page<CryptoWalletProfile> findByPspIdOrderByLatestRiskScoreDescUpdatedAtDesc(Long pspId, Pageable pageable);
    List<CryptoWalletProfile> findTop100ByStatusAndNextScreeningAtLessThanEqualOrderByNextScreeningAtAsc(
            String status, LocalDateTime dueAt);
}
