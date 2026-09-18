package com.posgateway.aml.repository.crypto;

import com.posgateway.aml.entity.crypto.VirtualAssetRegulatorAccessGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VirtualAssetRegulatorAccessGrantRepository extends JpaRepository<VirtualAssetRegulatorAccessGrant, Long> {
    Optional<VirtualAssetRegulatorAccessGrant> findByAccessKeyHash(String accessKeyHash);
    Optional<VirtualAssetRegulatorAccessGrant> findByIdAndPspId(Long id, Long pspId);
    List<VirtualAssetRegulatorAccessGrant> findByPspIdOrderByCreatedAtDesc(Long pspId);
}
