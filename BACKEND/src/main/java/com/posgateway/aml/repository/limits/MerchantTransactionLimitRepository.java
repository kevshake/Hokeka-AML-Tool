package com.posgateway.aml.repository.limits;

import com.posgateway.aml.entity.limits.MerchantTransactionLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface MerchantTransactionLimitRepository extends JpaRepository<MerchantTransactionLimit, Long> {
    Optional<MerchantTransactionLimit> findByMerchant_MerchantId(Long merchantId);
    Page<MerchantTransactionLimit> findByMerchant_Psp_PspId(Long pspId, Pageable pageable);
    List<MerchantTransactionLimit> findByMerchant_Psp_PspId(Long pspId);
    List<MerchantTransactionLimit> findByStatus(String status);
}

