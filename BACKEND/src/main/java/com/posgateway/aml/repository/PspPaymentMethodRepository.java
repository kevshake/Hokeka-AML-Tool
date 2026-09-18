package com.posgateway.aml.repository;

import com.posgateway.aml.entity.billing.PspPaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PspPaymentMethodRepository extends JpaRepository<PspPaymentMethod, Long> {
    Optional<PspPaymentMethod> findFirstByPspPspIdAndActiveTrueOrderByCreatedAtDesc(Long pspId);
}
