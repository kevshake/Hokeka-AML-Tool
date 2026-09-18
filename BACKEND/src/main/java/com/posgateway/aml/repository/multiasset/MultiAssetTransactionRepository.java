package com.posgateway.aml.repository.multiasset;

import com.posgateway.aml.entity.multiasset.MultiAssetTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import com.posgateway.aml.entity.multiasset.AssetClass;

public interface MultiAssetTransactionRepository extends JpaRepository<MultiAssetTransaction, Long> {
    Optional<MultiAssetTransaction> findByIdAndPspId(Long id, Long pspId);
    Optional<MultiAssetTransaction> findByPspIdAndExternalTransactionId(Long pspId, String externalTransactionId);
    List<MultiAssetTransaction> findTop500ByCustomerIdAndPspIdOrderByExecutedAtDesc(Long customerId, Long pspId);
    List<MultiAssetTransaction> findTop100ByCustomerIdAndPspIdOrderByExecutedAtDesc(Long customerId, Long pspId);
    Page<MultiAssetTransaction> findByPspIdAndAssetClassAndExecutedAtBetweenOrderByExecutedAtDesc(
            Long pspId, AssetClass assetClass, LocalDateTime from, LocalDateTime to, Pageable pageable);
    Page<MultiAssetTransaction> findByPspIdAndAssetClassOrderByExecutedAtDesc(
            Long pspId, AssetClass assetClass, Pageable pageable);

    @Query(value = "SELECT t.* FROM multi_asset_transactions t WHERE t.psp_id = :pspId AND ("
            + "t.metadata::jsonb ->> 'cyberIncidentNumber' = :incidentNumber OR "
            + "t.metadata::jsonb ->> 'cyberIncidentId' = CAST(:incidentId AS text)) "
            + "ORDER BY t.executed_at DESC LIMIT 20",
            nativeQuery = true)
    List<MultiAssetTransaction> findLinkedToCyberIncident(@Param("pspId") Long pspId,
            @Param("incidentId") Long incidentId,
            @Param("incidentNumber") String incidentNumber);
}
