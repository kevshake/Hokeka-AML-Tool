package com.posgateway.aml.service.edge.typology;

import java.util.List;

/**
 * Reference lists a compiled bundle carries in its {@code lists} section for {@code in_list}
 * conditions (high-risk countries, the PSP's payment blacklists, merchants onboarded in a
 * high-risk country). Values are master/list data, never transaction data.
 */
public interface EdgeReferenceLists {

    /** All active high-risk country codes ({@code high_risk_countries}). */
    String HIGH_RISK_COUNTRIES = "high_risk_countries";
    /** Merchant ids of the PSP whose onboarding country is high-risk. */
    String MERCHANTS_HIGH_RISK_COUNTRY = "merchants_high_risk_country";
    /** Prefix of the payment blacklist lists: {@code blacklist_<entry_type>}. */
    String BLACKLIST_PREFIX = "blacklist_";

    /**
     * The values of one named list for a PSP.
     *
     * @param pspId the PSP the bundle is for, or null for the global default rule set
     * @param list  list name (one of the constants, or {@code blacklist_<type>})
     * @return the values, possibly empty
     * @throws UncompilableRuleException when the list is unknown or its source cannot be read
     */
    List<String> values(Long pspId, String list);

    /**
     * Epoch seconds of the newest change to any list content this PSP's bundle can carry; folded
     * into the bundle version so a list addition reaches the Edge without a rule edit.
     */
    long stamp(Long pspId);

    /** Lists that are unavailable: every {@link #values} call fails with the given reason. */
    static EdgeReferenceLists unavailable(String reason) {
        return new EdgeReferenceLists() {
            /** Always fails: no source. */
            @Override
            public List<String> values(Long pspId, String list) {
                throw new UncompilableRuleException("reference list '" + list + "' is unavailable: " + reason);
            }

            /** No source, no stamp. */
            @Override
            public long stamp(Long pspId) {
                return 0L;
            }
        };
    }
}
