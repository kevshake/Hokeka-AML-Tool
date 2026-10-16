package com.posgateway.aml.repository.compliance;

import com.posgateway.aml.entity.compliance.AlertTransactionLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Persistence for alert↔transaction join rows used in consolidated alert investigations.
 */
@Repository
public interface AlertTransactionLinkRepository extends JpaRepository<AlertTransactionLink, Long> {

    /** Returns every alert linked to a transaction, newest links first by id. */
    List<AlertTransactionLink> findByTxnIdOrderByIdDesc(Long txnId);

    /** Returns every transaction contributing to an alert. */
    List<AlertTransactionLink> findByAlertIdOrderByPrimaryLinkDescIdDesc(Long alertId);
}
