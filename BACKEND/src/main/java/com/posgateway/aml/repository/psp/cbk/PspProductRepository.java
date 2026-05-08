package com.posgateway.aml.repository.psp.cbk;

import com.posgateway.aml.entity.psp.cbk.PspProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PspProductRepository extends JpaRepository<PspProduct, Long> {
    List<PspProduct> findByPspId(Long pspId);
    List<PspProduct> findByPspIdAndStatusCode(Long pspId, String statusCode);

    /**
     * Products live during [windowStart, windowEnd]. PspProduct does not yet
     * carry launch/retire dates so registry rows are gated by {@code createdAt}
     * &lt;= windowEnd to avoid leaking products created strictly after the
     * reporting window. Status filtering happens in the mapper.
     */
    @Query("SELECT p FROM PspProduct p WHERE p.pspId = :pspId " +
           "AND (p.createdAt IS NULL OR p.createdAt <= :windowEndAtEod)")
    List<PspProduct> findActiveInWindowInternal(@Param("pspId") Long pspId,
                                                @Param("windowEndAtEod") LocalDateTime windowEndAtEod);

    default List<PspProduct> findActiveInWindow(Long pspId, LocalDate windowStart, LocalDate windowEnd) {
        return findActiveInWindowInternal(pspId, windowEnd.atTime(23, 59, 59));
    }
}
