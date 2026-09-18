package com.posgateway.aml.repository.underwriting;

import com.posgateway.aml.entity.underwriting.MerchantActivationControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantActivationControlRepository extends JpaRepository<MerchantActivationControl, Long> {

    Optional<MerchantActivationControl> findFirstByMerchantIdAndActiveTrueOrderByCreatedAtDesc(Long merchantId);

    List<MerchantActivationControl> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
}
