package com.posgateway.aml.repository.enrichment;

import com.posgateway.aml.entity.enrichment.BinRange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link BinRange}. The PK is the BIN prefix string
 * (6-8 digits); longest-prefix-match is implemented at the service layer.
 */
@Repository
public interface BinRangeRepository extends JpaRepository<BinRange, String> {

    /**
     * All rows whose {@code bin_prefix} is a leading prefix of the given
     * card BIN. Caller picks the longest. Implemented as a native query so
     * the index on the PK is used (planner uses LIKE only when the pattern
     * starts with a fixed string).
     */
    @Query(value = "SELECT * FROM bin_ranges WHERE :bin LIKE bin_prefix || '%'",
           nativeQuery = true)
    List<BinRange> findMatchingPrefixes(@Param("bin") String bin);

    Optional<BinRange> findByBinPrefix(String binPrefix);
}
