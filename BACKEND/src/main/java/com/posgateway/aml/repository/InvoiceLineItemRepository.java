package com.posgateway.aml.repository;

import com.posgateway.aml.entity.psp.InvoiceLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Invoice Line Item Repository
 */
@Repository
public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItem, Long> {

    List<InvoiceLineItem> findByInvoice_InvoiceId(Long invoiceId);

    List<InvoiceLineItem> findByServiceType(String serviceType);
}
