package com.posgateway.aml.service.edge.typology;

import com.posgateway.aml.entity.risk.HighRiskCountry;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.risk.HighRiskCountryRepository;
import com.posgateway.aml.repository.security.PaymentBlacklistRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@link EdgeReferenceLists} read from the cloud's own reference tables — the same sources the
 * cloud SpEL path consults per transaction ({@code HighRiskCountryRepository},
 * {@code PaymentBlacklistService}, the merchant master data), so a compiled Edge rule matches
 * exactly the values the cloud rule matched.
 */
@Component
public class RepositoryEdgeReferenceLists implements EdgeReferenceLists {

    /** Blacklist entry types the compiled catalog reads ({@code payment_blacklist_entries.entry_type}). */
    static final Set<String> BLACKLIST_TYPES =
            Set.of("pan", "device", "wallet", "payment_details", "card_country", "reference_keyword");

    private final HighRiskCountryRepository highRiskCountries;
    private final PaymentBlacklistRepository blacklist;
    private final MerchantRepository merchants;

    /**
     * @param highRiskCountries {@code high_risk_countries}
     * @param blacklist         {@code payment_blacklist_entries}
     * @param merchants         merchant master data
     */
    public RepositoryEdgeReferenceLists(HighRiskCountryRepository highRiskCountries,
                                        PaymentBlacklistRepository blacklist, MerchantRepository merchants) {
        this.highRiskCountries = highRiskCountries;
        this.blacklist = blacklist;
        this.merchants = merchants;
    }

    /** Reads one list; an unknown list name or a PSP-scoped list without a PSP is uncompilable. */
    @Override
    public List<String> values(Long pspId, String list) {
        if (HIGH_RISK_COUNTRIES.equals(list)) {
            return highRiskCountries.findAll().stream()
                    .map(HighRiskCountry::getCountryCode)
                    .filter(c -> c != null && !c.isBlank())
                    .map(c -> c.trim().toUpperCase(Locale.ROOT))
                    .sorted().distinct().toList();
        }
        if (MERCHANTS_HIGH_RISK_COUNTRY.equals(list)) {
            if (pspId == null) {
                throw new UncompilableRuleException("merchant lists need a PSP; the global default bundle has none");
            }
            return merchants.idsInHighRiskCountries(pspId);
        }
        if (list.startsWith(BLACKLIST_PREFIX)) {
            String type = list.substring(BLACKLIST_PREFIX.length());
            if (!BLACKLIST_TYPES.contains(type)) {
                throw new UncompilableRuleException("unknown blacklist type '" + type + "'");
            }
            return blacklist.activeValuesForTenantOrPlatform(pspId, type, LocalDateTime.now()).stream()
                    .sorted().toList();
        }
        throw new UncompilableRuleException("unknown reference list '" + list + "'");
    }

    /**
     * Newest change across the sources: high-risk country additions, blacklist entries that
     * apply to the PSP, and the PSP's merchant master data.
     */
    @Override
    public long stamp(Long pspId) {
        long newest = 0L;
        for (HighRiskCountry c : highRiskCountries.findAll()) {
            newest = Math.max(newest, epoch(c.getAddedAt()));
        }
        newest = Math.max(newest, epoch(blacklist.newestCreatedAtForTenantOrPlatform(pspId)));
        if (pspId != null) {
            newest = Math.max(newest, epoch(merchants.newestUpdateForPsp(pspId)));
        }
        return newest;
    }

    /** Epoch seconds of a local timestamp (UTC), 0 for null. */
    private static long epoch(LocalDateTime t) {
        return t == null ? 0L : t.toEpochSecond(ZoneOffset.UTC);
    }
}
