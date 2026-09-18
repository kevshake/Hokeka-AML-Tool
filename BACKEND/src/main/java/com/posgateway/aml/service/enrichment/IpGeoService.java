package com.posgateway.aml.service.enrichment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Real IP→country GeoIP enrichment adapter.
 *
 * <p>Backed by an HTTP GeoIP lookup endpoint (env-configurable URL template).
 * Defaults to <a href="https://ipapi.co">ipapi.co</a>'s free JSON endpoint
 * which returns {@code country_code} for an IP. Results are cached in Redis
 * for 24 h since IP→country mappings are extremely stable.
 *
 * <p>Returns {@link Optional#empty()} when the upstream is disabled, blank,
 * or returns no usable country — callers must NOT silently fabricate
 * "UNKNOWN".
 */
@Service
public class IpGeoService {

    private static final Logger log = LoggerFactory.getLogger(IpGeoService.class);

    private static final String CACHE_PREFIX = "geoip:country:";
    private static final Duration TTL = Duration.ofHours(24);

    private final RedisTemplate<String, Object> redisTemplate;
    private final WebClient webClient;

    @Value("${enrichment.geoip.url:https://ipapi.co/{ip}/json/}")
    private String urlTemplate;

    @Value("${enrichment.geoip.enabled:true}")
    private boolean enabled;

    @Value("${enrichment.geoip.timeout-ms:1500}")
    private long timeoutMs;

    public IpGeoService(RedisTemplate<String, Object> redisTemplate, WebClient.Builder webClientBuilder) {
        this.redisTemplate = redisTemplate;
        this.webClient = webClientBuilder.build();
    }

    /**
     * Look up the ISO-3166 alpha-2 country code for the given IP.
     *
     * @return the 2-letter country code, or {@link Optional#empty()} when
     *         enrichment is disabled / unavailable / inconclusive.
     */
    public Optional<String> lookupCountry(String ip) {
        if (ip == null || ip.isBlank() || !enabled) {
            return Optional.empty();
        }
        String key = CACHE_PREFIX + ip;
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof String s && !s.isBlank()) {
                if ("__NONE__".equals(s)) return Optional.empty();
                return Optional.of(s);
            }
        } catch (Exception ex) {
            log.debug("Redis read fail for {}: {}", key, ex.getMessage());
        }

        try {
            String url = urlTemplate.replace("{ip}", ip);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofMillis(timeoutMs));
            String cc = null;
            if (body != null) {
                Object v = body.get("country_code");
                if (v == null) v = body.get("country");
                if (v instanceof String s && !s.isBlank()) cc = s.toUpperCase();
            }
            if (cc != null) {
                try { redisTemplate.opsForValue().set(key, cc, TTL); } catch (Exception ignored) {}
                return Optional.of(cc);
            }
            // Cache a negative result for a shorter window to avoid hammering upstream.
            try { redisTemplate.opsForValue().set(key, "__NONE__", Duration.ofHours(1)); } catch (Exception ignored) {}
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("GeoIP lookup failed for {}: {}", ip, ex.getMessage());
            return Optional.empty();
        }
    }
}
