package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The §7.12 session registry in Redis.
 *
 * <ul>
 *   <li>Hash {@code tunnel:psp:{pspId}}: field = {@code sessionId}, value = JSON
 *       {@code {edgeId, siteId, cpInstance, inflight, connectedAt, lastPongAt}}. Written on
 *       {@code hello.ack}, rewritten on pong and on every request start/finish, removed on close.
 *       Only the instance holding a session writes its field, so a whole-value rewrite never
 *       races with another instance.</li>
 *   <li>String {@code edge:lastseen:{edgeId}}: last instant a frame was received from the node,
 *       for {@code EDGE_OFFLINE.lastSeenAt}.</li>
 * </ul>
 *
 * <p>Nothing in Redis is payload: keys and values carry ids, counters and instants only (§7.12:
 * "the cloud MUST NOT use Redis streams, lists or keys for payloads").
 */
public class TunnelRegistry {

    private static final Logger log = LoggerFactory.getLogger(TunnelRegistry.class);

    static final String PSP_KEY_PREFIX = "tunnel:psp:";
    static final String LAST_SEEN_PREFIX = "edge:lastseen:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration staleAfter;

    /** One registry value. */
    public record Entry(long pspId, String sessionId, String edgeId, String siteId, String cpInstance,
                        int inflight, Instant connectedAt, Instant lastPongAt) {

        /** True when the entry has not been refreshed by a pong within {@code staleAfter}. */
        public boolean isStale(Instant now, Duration staleAfter) {
            return lastPongAt == null || lastPongAt.plus(staleAfter).isBefore(now);
        }
    }

    /** Creates the registry over a String-serialising Redis template. */
    public TunnelRegistry(StringRedisTemplate redis, ObjectMapper mapper, Duration staleAfter) {
        this.redis = redis;
        this.mapper = mapper;
        this.staleAfter = staleAfter;
    }

    /** Redis hash key of one PSP. */
    static String pspKey(long pspId) {
        return PSP_KEY_PREFIX + pspId;
    }

    /** Writes (or rewrites) one session's value. */
    public void put(Entry e) {
        ObjectNode v = mapper.createObjectNode();
        v.put("edgeId", e.edgeId());
        v.put("siteId", e.siteId());
        v.put("cpInstance", e.cpInstance());
        v.put("inflight", e.inflight());
        v.put("connectedAt", e.connectedAt().toString());
        v.put("lastPongAt", e.lastPongAt().toString());
        redis.opsForHash().put(pspKey(e.pspId()), e.sessionId(), v.toString());
    }

    /** Removes one session's value. */
    public void remove(long pspId, String sessionId) {
        redis.opsForHash().delete(pspKey(pspId), sessionId);
    }

    /** Every entry of a PSP, including stale ones; unparseable values are skipped and reaped. */
    public List<Entry> entries(long pspId) {
        Map<Object, Object> raw = redis.opsForHash().entries(pspKey(pspId));
        List<Entry> out = new ArrayList<>(raw.size());
        for (Map.Entry<Object, Object> f : raw.entrySet()) {
            Optional<Entry> parsed = parse(pspId, String.valueOf(f.getKey()), String.valueOf(f.getValue()));
            if (parsed.isPresent()) {
                out.add(parsed.get());
            } else {
                remove(pspId, String.valueOf(f.getKey()));
            }
        }
        return out;
    }

    /** Entries whose {@code lastPongAt} is fresh (§7.12: stale entries MUST be ignored). */
    public List<Entry> liveEntries(long pspId, Instant now) {
        return entries(pspId).stream().filter(e -> !e.isStale(now, staleAfter)).toList();
    }

    /** Records that a frame was received from {@code edgeId} at {@code at}. */
    public void markSeen(String edgeId, Instant at) {
        redis.opsForValue().set(LAST_SEEN_PREFIX + edgeId, at.toString());
    }

    /** Last instant a frame was received from {@code edgeId}, if ever. */
    public Optional<Instant> lastSeen(String edgeId) {
        String v = redis.opsForValue().get(LAST_SEEN_PREFIX + edgeId);
        if (v == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(v));
        } catch (java.time.format.DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Deletes every stale entry of every PSP (§7.12 "SHOULD be reaped"), using SCAN so the
     * keyspace walk never blocks Redis.
     *
     * @return the number of fields removed
     */
    public int reapStale(Instant now) {
        int removed = 0;
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(PSP_KEY_PREFIX + "*").count(200).build())) {
            cursor.forEachRemaining(keys::add);
        }
        for (String key : keys) {
            long pspId;
            try {
                pspId = Long.parseLong(key.substring(PSP_KEY_PREFIX.length()));
            } catch (NumberFormatException e) {
                continue;
            }
            for (Entry e : entries(pspId)) {
                if (e.isStale(now, staleAfter)) {
                    remove(pspId, e.sessionId());
                    removed++;
                }
            }
        }
        if (removed > 0) {
            log.info("Tunnel registry: reaped {} stale session entries", removed);
        }
        return removed;
    }

    /** Parses one hash value; empty when it is not a well-formed registry value. */
    private Optional<Entry> parse(long pspId, String sessionId, String json) {
        try {
            JsonNode n = mapper.readTree(json);
            return Optional.of(new Entry(pspId, sessionId, n.get("edgeId").asText(), n.get("siteId").asText(),
                    n.get("cpInstance").asText(), n.path("inflight").asInt(0),
                    Instant.parse(n.get("connectedAt").asText()), Instant.parse(n.get("lastPongAt").asText())));
        } catch (Exception e) {
            log.warn("Tunnel registry: dropping malformed entry for PSP {}", pspId);
            return Optional.empty();
        }
    }
}
