package com.posgateway.aml.repository.multiasset;

import com.posgateway.aml.entity.multiasset.AssetAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetAccountRepository extends JpaRepository<AssetAccount, Long> {
    Optional<AssetAccount> findByIdAndPspId(Long id, Long pspId);
    Optional<AssetAccount> findByPspIdAndExternalAccountId(Long pspId, String externalAccountId);
    List<AssetAccount> findByCustomerIdAndPspIdOrderByCreatedAtAsc(Long customerId, Long pspId);

    /** PSP-scoped asset account pages for Console list exports. */
    Page<AssetAccount> findByPspIdOrderByCreatedAtDesc(Long pspId, Pageable pageable);

    /** Platform-wide asset account pages for super-admin Console exports. */
    Page<AssetAccount> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
