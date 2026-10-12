package com.posgateway.aml.repository.alert;

import com.posgateway.aml.entity.alert.AlertTransactionLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Persistence for alert ↔ transaction many-to-many links.
 */
public interface AlertTransactionLinkRepository extends JpaRepository<AlertTransactionLink, AlertTransactionLink.AlertTransactionId> {

    /**
     * Returns all alert links for a transaction id (alerts-by-transaction lookup).
     */
    List<AlertTransactionLink> findByTxnIdOrderByLinkedAtDesc(Long txnId);

    /**
     * Returns all transactions linked to an alert.
     */
    List<AlertTransactionLink> findByAlertIdOrderByLinkedAtAsc(Long alertId);
}
