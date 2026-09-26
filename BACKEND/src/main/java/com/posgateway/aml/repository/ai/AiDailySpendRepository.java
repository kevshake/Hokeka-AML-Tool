package com.posgateway.aml.repository.ai;

import com.posgateway.aml.entity.ai.AiDailySpend;
import com.posgateway.aml.entity.ai.AiDailySpendId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface AiDailySpendRepository extends JpaRepository<AiDailySpend, AiDailySpendId> {

    @Modifying
    @Query(value = """
            INSERT INTO ai_daily_spend (psp_id, spend_date, call_count, total_tokens, estimated_usd)
            VALUES (:pspId, :spendDate, 1, :tokens, :cost)
            ON CONFLICT (psp_id, spend_date) DO UPDATE SET
                call_count = ai_daily_spend.call_count + 1,
                total_tokens = ai_daily_spend.total_tokens + :tokens,
                estimated_usd = ai_daily_spend.estimated_usd + :cost
            """, nativeQuery = true)
    void incrementSpend(@Param("pspId") long pspId,
                        @Param("spendDate") LocalDate spendDate,
                        @Param("tokens") long tokens,
                        @Param("cost") BigDecimal cost);
}
