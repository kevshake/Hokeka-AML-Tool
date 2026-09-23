package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.compliance.ComplianceDeadline;
import com.posgateway.aml.repository.ComplianceDeadlineRepository;
import com.posgateway.aml.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Bridges live CTR detection flags on transactions to compliance calendar deadlines so filing
 * is tracked automatically instead of relying on manual pull/report runs alone.
 */
@Service
public class CtrFilingBridgeService {

    private static final Logger log = LoggerFactory.getLogger(CtrFilingBridgeService.class);

    private final TransactionRepository transactionRepository;
    private final ComplianceDeadlineRepository deadlineRepository;

    @Value("${compliance.ctr.filing-deadline-days:5}")
    private int filingDeadlineDays;

    public CtrFilingBridgeService(TransactionRepository transactionRepository,
                                  ComplianceDeadlineRepository deadlineRepository) {
        this.transactionRepository = transactionRepository;
        this.deadlineRepository = deadlineRepository;
    }

    @Scheduled(cron = "${compliance.ctr.bridge-cron:0 15 4 * * *}")
    @Transactional
    public void bridgeDetectedCtrToFilingCalendar() {
        List<TransactionEntity> pending = transactionRepository.findReportableCtrWithoutFilingDeadline(
                PageRequest.of(0, 200));
        if (pending.isEmpty()) {
            return;
        }
        int created = 0;
        for (TransactionEntity tx : pending) {
            if (deadlineRepository.findBySourceTypeAndSourceId("TRANSACTION", tx.getTxnId()).isPresent()) {
                continue;
            }
            ComplianceDeadline deadline = new ComplianceDeadline();
            deadline.setDeadlineType("CTR_FILING");
            deadline.setSourceType("TRANSACTION");
            deadline.setSourceId(tx.getTxnId());
            deadline.setPspId(tx.getPspId());
            deadline.setJurisdiction("KE");
            deadline.setDescription(String.format(
                    "Auto-scheduled CTR filing for transaction %d (merchant %s, USD equiv %s)",
                    tx.getTxnId(), tx.getMerchantId(),
                    tx.getCtrUsdEquivalent() != null ? tx.getCtrUsdEquivalent().toPlainString() : "n/a"));
            deadline.setDeadlineDate(nextBusinessFriday(
                    tx.getTxnTs() != null ? tx.getTxnTs() : LocalDateTime.now()));
            deadlineRepository.save(deadline);
            created++;
        }
        log.info("CTR filing bridge: created {} compliance deadline(s) from {} detected transaction(s)",
                created, pending.size());
    }

    static LocalDateTime nextBusinessFriday(LocalDateTime from) {
        LocalDateTime candidate = from.plusDays(1).withHour(17).withMinute(0).withSecond(0).withNano(0);
        while (candidate.getDayOfWeek() == DayOfWeek.SATURDAY
                || candidate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            candidate = candidate.plusDays(1);
        }
        int daysUntilFriday = (DayOfWeek.FRIDAY.getValue() - candidate.getDayOfWeek().getValue() + 7) % 7;
        return candidate.plusDays(daysUntilFriday);
    }
}
