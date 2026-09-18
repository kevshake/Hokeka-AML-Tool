package com.posgateway.aml.repository.reporting;

import com.posgateway.aml.entity.reporting.ReportScheduleHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReportScheduleHistoryRepository extends JpaRepository<ReportScheduleHistory, Long> {
    List<ReportScheduleHistory> findByScheduleIdOrderByScheduledForDesc(Long scheduleId);
}
