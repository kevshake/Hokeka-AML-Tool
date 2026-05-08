package com.posgateway.aml.service.enrichment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Real BIN→issuer-country lookup against the {@code bin_ranges} Postgres
 * table. The table is the source of truth for BIN-prefix → ISO country
 * mappings (issuer-country only — no PCI-restricted attributes).
 *
 * <p>Schema (expected, flagged for migration agent):
 * <pre>
 *   CREATE TABLE bin_ranges (
 *       bin_start  bigint        NOT NULL,
 *       bin_end    bigint        NOT NULL,
 *       country    char(2)       NOT NULL,
 *       brand      varchar(32),
 *       PRIMARY KEY (bin_start, bin_end)
 *   );
 *   CREATE INDEX idx_bin_ranges_lookup ON bin_ranges (bin_start, bin_end);
 * </pre>
 *
 * <p>The service caches positive and negative results in Redis (30 days for
 * hits, 6 hours for misses) and returns {@link Optional#empty()} when the
 * BIN cannot be resolved or the table is missing.
 */
@Service
public class BinLookupService {

    private static final Logger log = LoggerFactory.getLogger(BinLookupService.class);

    private static final String CACHE_PREFIX = "bin:country:";
    private static final Duration HIT_TTL = Duration.ofDays(30);
    private static final Duration MISS_TTL = Duration.ofHours(6);
    private static final String NONE_SENTINEL = "__NONE__";

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public BinLookupService(JdbcTemplate jdbcTemplate,
                            RedisTemplate<String, Object> redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Look up issuer country (ISO-3166 alpha-2) by card BIN.
     *
     * @param bin the leading 6+ digits of a PAN; PAN itself MUST NOT be passed
     * @return the country code, or empty when not found / table absent
     */
    public Optional<String> lookupCountry(String bin) {
        if (bin == null || bin.isBlank()) {
            return Optional.empty();
        }
        String trimmed = bin.replaceAll("\\D", "");
        if (trimmed.length() < 6) {
            return Optional.empty();
        }
        // Use the leading 8 digits for the longest-prefix match (industry norm).
        String prefix = trimmed.length() >= 8 ? trimmed.substring(0, 8) : trimmed;
        long binNumber;
        try {
            binNumber = Long.parseLong(prefix);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }

        String key = CACHE_PREFIX + prefix;
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof String s) {
                if (NONE_SENTINEL.equals(s)) return Optional.empty();
                if (!s.isBlank()) return Optional.of(s);
            }
        } catch (Exception ex) {
            log.debug("Redis read fail bin {}: {}", prefix, ex.getMessage());
        }

        try {
            String sql = "SELECT country FROM bin_ranges " +
                    "WHERE ? BETWEEN bin_start AND bin_end " +
                    "ORDER BY (bin_end - bin_start) ASC LIMIT 1";
            String country = jdbcTemplate.queryForObject(sql, String.class, binNumber);
            if (country != null && !country.isBlank()) {
                try { redisTemplate.opsForValue().set(key, country.toUpperCase(), HIT_TTL); } catch (Exception ignored) {}
                return Optional.of(country.toUpperCase());
            }
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            // No row matched — fall through to negative cache.
        } catch (DataAccessException ex) {
            // Table missing in test profiles or other DB error — log once, return empty.
            log.warn("BIN lookup DB error for prefix {} ({}). " +
                    "FIXME(go-live, bin_ranges-migration-pending)", prefix, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("BIN lookup unexpected error: {}", ex.getMessage());
            return Optional.empty();
        }
        try { redisTemplate.opsForValue().set(key, NONE_SENTINEL, MISS_TTL); } catch (Exception ignored) {}
        return Optional.empty();
    }
}
