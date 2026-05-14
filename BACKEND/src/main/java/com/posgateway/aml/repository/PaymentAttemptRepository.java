package com.posgateway.aml.repository;

import com.posgateway.aml.entity.billing.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link PaymentAttempt}.
 */
@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    List<PaymentAttempt> findByInvoiceId(Long invoiceId);

    Optional<PaymentAttempt> findByMpesaCheckoutRequestId(String checkoutRequestId);

    List<PaymentAttempt> findByPspIdOrderByCreatedAtDesc(Long pspId);
}
