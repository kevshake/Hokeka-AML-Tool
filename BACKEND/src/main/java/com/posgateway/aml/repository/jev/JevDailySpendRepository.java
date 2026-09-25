package com.posgateway.aml.repository.jev;

import com.posgateway.aml.entity.jev.JevDailySpend;
import com.posgateway.aml.entity.jev.JevDailySpendId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface JevDailySpendRepository extends JpaRepository<JevDailySpend, JevDailySpendId> {

    @Modifying
    @Query(value = """
            INSERT INTO jev_daily_spend (psp_id, spend_date, call_count, total_tokens, estimated_usd)
            VALUES (:pspId, :spendDate, 1, :tokens, :cost)
            ON CONFLICT (psp_id, spend_date) DO UPDATE SET
                call_count = jev_daily_spend.call_count + 1,
                total_tokens = jev_daily_spend.total_tokens + :tokens,
                estimated_usd = jev_daily_spend.estimated_usd + :cost
            """, nativeQuery = true)
    void incrementSpend(@Param("pspId") long pspId,
                        @Param("spendDate") LocalDate spendDate,
                        @Param("tokens") long tokens,
                        @Param("cost") BigDecimal cost);
}
