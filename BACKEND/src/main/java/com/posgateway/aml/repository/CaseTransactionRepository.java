package com.posgateway.aml.repository;

import com.posgateway.aml.entity.compliance.CaseTransaction;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Case Transactions
 */
@Repository
public interface CaseTransactionRepository extends JpaRepository<CaseTransaction, Long> {

    /**
     * Find all transactions for a case
     */
    List<CaseTransaction> findByComplianceCase(ComplianceCase complianceCase);

    /**
     * Find transactions by case ID
     */
    List<CaseTransaction> findByComplianceCase_Id(Long caseId);
}

