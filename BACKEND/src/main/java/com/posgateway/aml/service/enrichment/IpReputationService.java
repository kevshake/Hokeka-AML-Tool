package com.posgateway.aml.service.enrichment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Classifies the reputation of a connection IP to catch IP manipulation and anonymised
 * (VPN / proxy / Tor / datacenter) connections coming from customers or PSPs.
 *
 * <p>The high-confidence checks are fully local and fast — no external call — so they add
 * negligible latency to the transaction path:
 * <ul>
 *   <li><b>malformed</b> — not a parseable IP (garbage / spoofed value);</li>
 *   <li><b>private / reserved</b> — loopback, RFC-1918 private, link-local, carrier-grade NAT
 *       (100.64.0.0/10), multicast or wildcard. A real customer/PSP connection is a public IP,
 *       so these indicate a masked or manipulated source;</li>
 *   <li><b>anonymising</b> — the IP falls in a configured VPN / proxy / datacenter CIDR set
 *       ({@code ip.reputation.anonymizing-cidrs}).</li>
 * </ul>
 *
 * <p>A <b>geo mismatch</b> (the IP's resolved country differs from the country declared on the
 * transaction) is also surfaced as a lower-confidence signal for the caller to weigh.
 */
@Service
public class IpReputationService {

    private static final Logger log = LoggerFactory.getLogger(IpReputationService.class);

    @Value("${ip.reputation.enabled:true}")
    private boolean enabled;

    /**
     * Comma-separated IPv4 CIDR ranges known to be VPN / proxy / Tor-exit / datacenter. Empty by
     * default (ops supply their threat-intel ranges); the mechanism is always active.
     */
    @Value("${ip.reputation.anonymizing-cidrs:}")
    private String anonymizingCidrsRaw;

    private volatile List<long[]> anonymizingRanges; // [networkStart, networkEnd] inclusive, IPv4

    /** Result of classifying an IP. */
    public record IpAssessment(
            boolean present,
            boolean malformed,
            boolean privateOrReserved,
            boolean anonymizing,
            boolean geoMismatch,
            String category) {

        /** True when the IP looks manipulated or masked (high-confidence, deterministic). */
        public boolean manipulated() {
            return malformed || privateOrReserved || anonymizing;
        }
    }

    /**
     * @param ip             the connection IP (customer or PSP origin)
     * @param declaredCountry country declared on the transaction/customer record (may be null)
     * @param ipGeoCountry    country the IP actually resolves to via GeoIP (may be null)
     */
    public IpAssessment assess(String ip, String declaredCountry, String ipGeoCountry) {
        if (!enabled) {
            return new IpAssessment(ip != null && !ip.isBlank(), false, false, false, false, "DISABLED");
        }
        if (ip == null || ip.isBlank()) {
            return new IpAssessment(false, false, false, false, false, "MISSING");
        }
        String trimmed = ip.trim();

        InetAddress addr;
        try {
            addr = parseNumeric(trimmed);
        } catch (UnknownHostException e) {
            return new IpAssessment(true, true, false, false, false, "MALFORMED");
        }

        boolean privateOrReserved = addr.isLoopbackAddress()
                || addr.isAnyLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isMulticastAddress()
                || isCarrierGradeNat(addr);

        boolean anonymizing = !privateOrReserved && isInAnonymizingRange(addr);

        boolean geoMismatch = declaredCountry != null && !declaredCountry.isBlank()
                && ipGeoCountry != null && !ipGeoCountry.isBlank()
                && !declaredCountry.trim().equalsIgnoreCase(ipGeoCountry.trim());

        String category = privateOrReserved ? "PRIVATE_OR_RESERVED"
                : anonymizing ? "ANONYMIZING"
                : geoMismatch ? "GEO_MISMATCH"
                : "PUBLIC";

        return new IpAssessment(true, false, privateOrReserved, anonymizing, geoMismatch, category);
    }

    /**
     * Parse an IP literal WITHOUT ever triggering a DNS lookup (which would be slow and could hang
     * the transaction path). IPv4 is decoded octet-by-octet into bytes; IPv6 literals (containing
     * ':') go through getByName, which does not resolve DNS for a bracket-free numeric literal.
     */
    private InetAddress parseNumeric(String ip) throws UnknownHostException {
        if (ip.indexOf(':') >= 0) {
            if (!ip.matches("[0-9a-fA-F:.]+")) {
                throw new UnknownHostException("non-numeric IPv6: " + ip);
            }
            return InetAddress.getByName(ip); // IPv6 literal → no DNS
        }
        String[] octets = ip.split("\\.", -1);
        if (octets.length != 4) {
            throw new UnknownHostException("not an IPv4 address: " + ip);
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            if (!octets[i].matches("\\d{1,3}")) {
                throw new UnknownHostException("bad octet in " + ip);
            }
            int v = Integer.parseInt(octets[i]);
            if (v > 255) {
                throw new UnknownHostException("octet > 255 in " + ip);
            }
            bytes[i] = (byte) v;
        }
        return InetAddress.getByAddress(bytes); // raw bytes → never a DNS lookup
    }

    /** 100.64.0.0/10 — shared address space (CGN), not covered by isSiteLocalAddress(). */
    private boolean isCarrierGradeNat(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (b.length != 4) {
            return false;
        }
        int first = b[0] & 0xFF;
        int second = b[1] & 0xFF;
        return first == 100 && second >= 64 && second <= 127;
    }

    private boolean isInAnonymizingRange(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (b.length != 4) {
            return false; // CIDR set is IPv4-only for now
        }
        long ipVal = toLong(b);
        for (long[] range : anonymizingRanges()) {
            if (ipVal >= range[0] && ipVal <= range[1]) {
                return true;
            }
        }
        return false;
    }

    private List<long[]> anonymizingRanges() {
        List<long[]> ranges = this.anonymizingRanges;
        if (ranges == null) {
            ranges = parseCidrs(anonymizingCidrsRaw);
            this.anonymizingRanges = ranges;
        }
        return ranges;
    }

    private List<long[]> parseCidrs(String raw) {
        List<long[]> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String token : raw.split(",")) {
            String cidr = token.trim();
            if (cidr.isEmpty()) {
                continue;
            }
            try {
                String[] parts = cidr.split("/");
                InetAddress net = InetAddress.getByName(parts[0].trim());
                byte[] nb = net.getAddress();
                if (nb.length != 4) {
                    continue;
                }
                int prefix = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 32;
                if (prefix < 0 || prefix > 32) {
                    continue;
                }
                long base = toLong(nb);
                long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
                long start = base & mask;
                long end = start | (~mask & 0xFFFFFFFFL);
                out.add(new long[]{start, end});
            } catch (Exception e) {
                log.warn("Ignoring invalid anonymizing CIDR '{}': {}", cidr, e.getMessage());
            }
        }
        return out;
    }

    private static long toLong(byte[] b) {
        return ((long) (b[0] & 0xFF) << 24)
                | ((b[1] & 0xFF) << 16)
                | ((b[2] & 0xFF) << 8)
                | (b[3] & 0xFF);
    }

    /** Force a re-parse of the configured anonymizing CIDR set (e.g. after a config refresh). */
    public void refreshRanges() {
        this.anonymizingRanges = parseCidrs(anonymizingCidrsRaw);
    }
}
