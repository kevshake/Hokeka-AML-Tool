package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.residency.EdgeConfigDocument;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/**
 * The {@code CTR_AGGREGATE} item of {@code filing.extract} (EDGE_DATA_RESIDENCY_CONTRACT.md §7.6):
 * the card's cash total for the anchor's business day (config {@code ctr.businessDayTimezone}),
 * converted to {@code ctr.reportingCurrency} with the config FX in force, with the threshold, the
 * count, the contributing ids and the {@code configVersion} used.
 *
 * <p>FX: {@code rates[C]} = units of {@code fx.base} per one unit of currency C (§6.1). An amount
 * of C is converted C → base → reporting; amounts are summed exactly and rounded once, half-even,
 * to the reporting currency's minor unit. A currency without a rate (other than the base and the
 * reporting currency themselves) cannot be converted, so the extract fails rather than under-report.
 */
public final class CtrAggregator {

    /** Most transactions one aggregate may list ({@code edgeAssessmentIds} maxItems). */
    public static final int MAX_IDS = 10_000;

    private final ObjectMapper mapper;

    /** @param mapper JSON mapper */
    public CtrAggregator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** The anchor's business day in the CTR zone. */
    public static LocalDate businessDate(Instant evaluatedAt, EdgeConfigDocument.Ctr ctr) {
        return evaluatedAt.atZone(ctr.timezone()).toLocalDate();
    }

    /** UTC start of a business day in the CTR zone. */
    public static Instant dayStart(LocalDate day, EdgeConfigDocument.Ctr ctr) {
        return day.atStartOfDay(ctr.timezone()).toInstant();
    }

    /**
     * Converts and sums the day's cash transactions.
     *
     * @throws TunnelException {@code INTERNAL} when a currency has no FX rate or the reporting
     *                         currency has no threshold (a config defect, reported without values)
     */
    public ObjectNode aggregate(UUID anchor, LocalDate businessDate, List<LedgerReads.CashTxn> cash,
                                EdgeConfigDocument config) throws TunnelException {
        EdgeConfigDocument.Ctr ctr = config.ctr();
        String reporting = ctr.reportingCurrency();
        Long threshold = ctr.thresholds().get(reporting);
        if (threshold == null) {
            throw new TunnelException(TunnelErrorCode.INTERNAL, "the Edge config has no CTR threshold for its reporting currency");
        }
        BigDecimal total = BigDecimal.ZERO;
        ObjectNode item = mapper.createObjectNode();
        item.put("kind", "CTR_AGGREGATE");
        item.put("anchorEdgeAssessmentId", anchor.toString());
        item.put("businessDate", businessDate.toString());
        item.put("timezone", ctr.timezone().getId());
        item.put("reportingCurrency", reporting);
        ArrayNode ids = mapper.createArrayNode();
        for (LedgerReads.CashTxn t : cash) {
            total = total.add(toReporting(t.amountMinor(), t.currency(), ctr));
            ids.add(t.id().toString());
        }
        long totalMinor = total.movePointRight(digits(reporting)).setScale(0, RoundingMode.HALF_EVEN).longValueExact();
        item.put("cashTotalMinor", totalMinor);
        item.put("thresholdMinor", threshold);
        item.put("txnCount", cash.size());
        item.set("edgeAssessmentIds", ids);
        item.put("fxAsOf", TunnelTime.millis(ctr.fxAsOf()));
        item.put("configVersion", config.configVersion());
        return item;
    }

    /** One amount (minor units of {@code currency}) in major units of the reporting currency. */
    static BigDecimal toReporting(long amountMinor, String currency, EdgeConfigDocument.Ctr ctr) throws TunnelException {
        BigDecimal major = BigDecimal.valueOf(amountMinor).movePointLeft(digits(currency));
        String reporting = ctr.reportingCurrency();
        if (currency.equals(reporting)) {
            return major;
        }
        BigDecimal inBase = currency.equals(ctr.fxBase()) ? major : major.multiply(rate(currency, ctr));
        return reporting.equals(ctr.fxBase()) ? inBase : inBase.divide(rate(reporting, ctr), MathContext.DECIMAL128);
    }

    /** {@code rates[currency]}; INTERNAL when missing or zero. */
    private static BigDecimal rate(String currency, EdgeConfigDocument.Ctr ctr) throws TunnelException {
        BigDecimal r = ctr.rates().get(currency);
        if (r == null || r.signum() == 0) {
            throw new TunnelException(TunnelErrorCode.INTERNAL,
                    "the Edge config has no usable FX rate for a currency of the CTR day");
        }
        return r;
    }

    /** ISO 4217 minor-unit digits (2 when the JDK does not know the code). */
    static int digits(String currency) {
        try {
            int d = Currency.getInstance(currency).getDefaultFractionDigits();
            return d < 0 ? 0 : d;
        } catch (IllegalArgumentException e) {
            return 2;
        }
    }
}
