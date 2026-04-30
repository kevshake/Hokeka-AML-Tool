package com.posgateway.aml.repository;

import com.posgateway.aml.entity.psp.ApiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API Usage Log Repository
 */
@Repository
public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, Long> {

        List<ApiUsageLog> findByPsp_PspId(Long pspId);

        List<ApiUsageLog> findByPsp_PspIdAndRequestTimestampBetween(
                        Long pspId,
                        LocalDateTime start,
                        LocalDateTime end);

        @Query("SELECT a FROM ApiUsageLog a WHERE a.psp.pspId = :pspId " +
                        "AND a.serviceType = :serviceType " +
                        "AND a.requestTimestamp BETWEEN :start AND :end")
        List<ApiUsageLog> findByPspAndServiceAndPeriod(
                        @Param("pspId") Long pspId,
                        @Param("serviceType") String serviceType,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        @Query("SELECT COUNT(a) FROM ApiUsageLog a WHERE a.psp.pspId = :pspId " +
                        "AND a.billable = true " +
                        "AND a.requestTimestamp BETWEEN :start AND :end")
        long countBillableRequests(
                        @Param("pspId") Long pspId,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        @Query("SELECT SUM(a.costAmount) FROM ApiUsageLog a WHERE a.psp.pspId = :pspId " +
                        "AND a.billable = true " +
                        "AND a.requestTimestamp BETWEEN :start AND :end")
        BigDecimal sumCostByPspAndPeriod(
                        @Param("pspId") Long pspId,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        @Query("SELECT a.serviceType, COUNT(a), SUM(a.costAmount) " +
                        "FROM ApiUsageLog a " +
                        "WHERE a.psp.pspId = :pspId " +
                        "AND a.billable = true " +
                        "AND a.requestTimestamp BETWEEN :start AND :end " +
                        "GROUP BY a.serviceType")
        List<Object[]> getUsageSummaryByService(
                        @Param("pspId") Long pspId,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        /**
         * Count billable checks for billing engine
         */
        default int countBillableByPspAndPeriod(Long pspId, LocalDateTime start, LocalDateTime end) {
                return (int) countBillableRequests(pspId, start, end);
        }
}
