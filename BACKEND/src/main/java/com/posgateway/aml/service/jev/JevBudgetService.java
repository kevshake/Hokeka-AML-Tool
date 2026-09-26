package com.posgateway.aml.service.jev;

import com.posgateway.aml.config.jev.JevProperties;
import com.posgateway.aml.repository.jev.JevDailySpendRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class JevBudgetService {

    private final JevProperties properties;
    private final JevDailySpendRepository dailySpendRepository;

    public JevBudgetService(JevProperties properties, JevDailySpendRepository dailySpendRepository) {
        this.properties = properties;
        this.dailySpendRepository = dailySpendRepository;
    }

    public boolean isBudgetExceeded(Long pspId) {
        if (pspId == null) {
            return false;
        }
        int callBudget = properties.getDailyCallBudgetPerPsp();
        long tokenCap = properties.getDailyInputTokenCapPerPsp();
        if (callBudget <= 0 && tokenCap <= 0) {
            return false;
        }
        return dailySpendRepository.findById(new com.posgateway.aml.entity.jev.JevDailySpendId(pspId, LocalDate.now()))
                .map(row -> {
                    if (callBudget > 0 && row.getCallCount() >= callBudget) {
                        return true;
                    }
                    return tokenCap > 0 && row.getTotalTokens() >= tokenCap;
                })
                .orElse(false);
    }

    @Transactional
    public void recordSpend(Long pspId, long tokens, BigDecimal cost) {
        if (pspId == null) {
            return;
        }
        dailySpendRepository.incrementSpend(
                pspId,
                LocalDate.now(),
                tokens,
                cost == null ? BigDecimal.ZERO : cost);
    }
}
