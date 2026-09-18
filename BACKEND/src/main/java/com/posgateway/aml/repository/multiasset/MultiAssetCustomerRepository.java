package com.posgateway.aml.repository.multiasset;

import com.posgateway.aml.entity.multiasset.MultiAssetCustomer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MultiAssetCustomerRepository extends JpaRepository<MultiAssetCustomer, Long> {
    Optional<MultiAssetCustomer> findByIdAndPspId(Long id, Long pspId);
    Optional<MultiAssetCustomer> findByPspIdAndExternalCustomerId(Long pspId, String externalCustomerId);
    Page<MultiAssetCustomer> findByPspId(Long pspId, Pageable pageable);
    Page<MultiAssetCustomer> findByPspIdAndDisplayNameContainingIgnoreCase(Long pspId, String name, Pageable pageable);
}
