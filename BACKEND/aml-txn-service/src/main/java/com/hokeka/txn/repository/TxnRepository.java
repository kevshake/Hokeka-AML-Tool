package com.hokeka.txn.repository;

import com.hokeka.txn.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TxnRepository extends JpaRepository<Transaction, String> {
  Page<Transaction> findByPspIdOrderByTxnTsDesc(String pspId, Pageable pageable);
}
