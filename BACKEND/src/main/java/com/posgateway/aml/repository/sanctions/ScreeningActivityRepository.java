package com.posgateway.aml.repository.sanctions;

import com.posgateway.aml.entity.sanctions.ScreeningActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Query persisted screening runs for API evidence and investigation trails.
 */
@Repository
public interface ScreeningActivityRepository extends JpaRepository<ScreeningActivity, Long> {

    Page<ScreeningActivity> findByTxnIdOrderByCreatedAtDesc(Long txnId, Pageable pageable);

    Page<ScreeningActivity> findByMerchantIdOrderByCreatedAtDesc(Long merchantId, Pageable pageable);

    List<ScreeningActivity> findByPspIdAndTxnIdOrderByCreatedAtDesc(Long pspId, Long txnId);
}
