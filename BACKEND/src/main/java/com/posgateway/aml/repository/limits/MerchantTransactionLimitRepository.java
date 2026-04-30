package com.posgateway.aml.repository.limits;

import com.posgateway.aml.entity.limits.MerchantTransactionLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantTransactionLimitRepository extends JpaRepository<MerchantTransactionLimit, Long> {
    Optional<MerchantTransactionLimit> findByMerchant_MerchantId(Long merchantId);
    List<MerchantTransactionLimit> findByStatus(String status);
}

