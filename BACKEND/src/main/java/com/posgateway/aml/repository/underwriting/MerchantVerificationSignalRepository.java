package com.posgateway.aml.repository.underwriting;

import com.posgateway.aml.entity.underwriting.MerchantVerificationSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MerchantVerificationSignalRepository extends JpaRepository<MerchantVerificationSignal, Long> {

    List<MerchantVerificationSignal> findByMerchantIdOrderByObservedAtDesc(Long merchantId);

    List<MerchantVerificationSignal> findByRunIdOrderByObservedAtAsc(String runId);
}
