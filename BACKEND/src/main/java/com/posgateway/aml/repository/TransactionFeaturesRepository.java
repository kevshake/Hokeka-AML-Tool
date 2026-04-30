package com.posgateway.aml.repository;

import com.posgateway.aml.entity.TransactionFeatures;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Transaction Features
 */
@Repository
public interface TransactionFeaturesRepository extends JpaRepository<TransactionFeatures, Long> {

    /**
     * Find features by transaction ID
     */
    TransactionFeatures findByTxnId(Long txnId);

    /**
     * Find labeled transactions for training
     */
    @Query("SELECT tf FROM TransactionFeatures tf WHERE tf.label IS NOT NULL")
    List<TransactionFeatures> findLabeledTransactions();

    /**
     * Find transactions scored in time range
     */
    @Query("SELECT tf FROM TransactionFeatures tf WHERE tf.scoredAt >= :startTime AND tf.scoredAt <= :endTime")
    List<TransactionFeatures> findScoredInTimeRange(@Param("startTime") LocalDateTime startTime, 
                                                      @Param("endTime") LocalDateTime endTime);
}

