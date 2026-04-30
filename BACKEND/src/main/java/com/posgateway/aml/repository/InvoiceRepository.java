package com.posgateway.aml.repository;

import com.posgateway.aml.entity.psp.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Invoice Repository
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    List<Invoice> findByPsp_PspId(Long pspId);

    List<Invoice> findByPsp_PspIdOrderByBillingPeriodEndDesc(Long pspId);

    List<Invoice> findByStatus(String status);

    @Query("SELECT i FROM Invoice i WHERE i.status = :status AND i.dueDate < :date")
    List<Invoice> findOverdueInvoices(@Param("status") String status, @Param("date") LocalDate date);

    @Query("SELECT i FROM Invoice i WHERE i.psp.pspId = :pspId " +
            "AND i.billingPeriodStart = :periodStart " +
            "AND i.billingPeriodEnd = :periodEnd")
    Optional<Invoice> findByPspAndPeriod(
            @Param("pspId") Long pspId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    @Query("SELECT SUM(i.totalAmount) FROM Invoice i WHERE i.psp.pspId = :pspId AND i.status = 'PAID'")
    BigDecimal sumPaidAmountByPsp(@Param("pspId") Long pspId);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.status = :status")
    long countByStatus(@Param("status") String status);
}
