package com.posgateway.aml.service.enrichment;

import com.posgateway.aml.entity.enrichment.BinRange;
import com.posgateway.aml.repository.enrichment.BinRangeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * BIN → card metadata lookup against the {@code bin_ranges} Postgres table.
 *
 * <p>Schema (created by V130): {@code bin_prefix VARCHAR(8) PRIMARY KEY,
 * issuer, issuer_country, card_brand, card_type, card_class}. Longest-prefix
 * match wins.
 *
 * <p>Results are cached in Redis under {@code bin:<prefix>} for 24h. A
 * negative-cache sentinel ({@code __NONE__}) is written for misses with a
 * 6h TTL so the same probed BIN doesn't re-hit Postgres on every txn.
 */
@Service
public class BinLookupService {

    private static final Logger log = LoggerFactory.getLogger(BinLookupService.class);

    private static final String CACHE_PREFIX = "bin:";
    private static final Duration HIT_TTL  = Duration.ofHours(24);
    private static final Duration MISS_TTL = Duration.ofHours(6);
    private static final String NONE_SENTINEL = "__NONE__";

    private final BinRangeRepository binRangeRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public BinLookupService(BinRangeRepository binRangeRepository,
                            RedisTemplate<String, Object> redisTemplate) {
        this.binRangeRepository = binRangeRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Look up issuer country (ISO-3166 alpha-2) by card BIN.
     *
     * @param bin the leading 6+ digits of a PAN; PAN itself MUST NOT be passed
     * @return the country code, or empty when not found / table absent
     */
    public Optional<String> lookupCountry(String bin) {
        return lookup(bin).map(BinRange::getIssuerCountry).filter(s -> s != null && !s.isBlank());
    }

    /**
     * Look up the full BIN row by prefix (longest-prefix match). Returns the
     * cached row when warm; otherwise performs a single SELECT and writes
     * back the country / brand into Redis for the next caller.
     */
    public Optional<BinRange> lookup(String bin) {
        if (bin == null || bin.isBlank()) {
            return Optional.empty();
        }
        String trimmed = bin.replaceAll("\\D", "");
        if (trimmed.length() < 6) {
            return Optional.empty();
        }
        String probe = trimmed.length() >= 8 ? trimmed.substring(0, 8) : trimmed;

        // Negative cache short-circuit (cheap miss path).
        String cacheKey = CACHE_PREFIX + probe;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof String s && NONE_SENTINEL.equals(s)) {
                return Optional.empty();
            }
        } catch (Exception ex) {
            log.debug("Redis read fail bin {}: {}", probe, ex.getMessage());
        }

        Optional<BinRange> match;
        try {
            List<BinRange> candidates = binRangeRepository.findMatchingPrefixes(probe);
            match = candidates.stream()
                    .max(Comparator.comparingInt(b -> b.getBinPrefix() == null ? 0 : b.getBinPrefix().length()));
        } catch (DataAccessException ex) {
            log.warn("BIN lookup DB error for prefix {} ({})", probe, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("BIN lookup unexpected error: {}", ex.getMessage());
            return Optional.empty();
        }

        try {
            if (match.isPresent()) {
                String country = match.get().getIssuerCountry();
                if (country != null && !country.isBlank()) {
                    redisTemplate.opsForValue().set(cacheKey, country.toUpperCase(), HIT_TTL);
                }
            } else {
                redisTemplate.opsForValue().set(cacheKey, NONE_SENTINEL, MISS_TTL);
            }
        } catch (Exception ignored) {
            // Cache writes are best-effort.
        }
        return match;
    }
}
