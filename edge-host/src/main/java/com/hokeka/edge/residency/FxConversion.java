package com.hokeka.edge.residency;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Currency;
import java.util.Optional;

/**
 * Currency conversion with the Edge Config FX (EDGE_DATA_RESIDENCY_CONTRACT.md §6.1):
 * {@code rates[C]} = units of {@code fx.base} per one unit of currency C. An amount of C is
 * converted C → base → target. A currency (other than the base and the target themselves) without a
 * usable rate cannot be converted: the result is empty, and every caller fails visibly rather than
 * assume a value. Shared by the evaluate-time CTR/limit checks and {@code filing.extract}.
 */
public final class FxConversion {

    private FxConversion() {
    }

    /**
     * One amount in major units of {@code target}.
     *
     * @param amountMinor amount in minor units of {@code currency}
     * @param currency    ISO 4217 code of the amount
     * @param target      ISO 4217 code to convert to
     * @param ctr         the config CTR section holding the FX table
     * @return the converted major-unit amount (exact, DECIMAL128 on division), or empty when a
     *         needed rate is missing or zero
     */
    public static Optional<BigDecimal> toMajor(long amountMinor, String currency, String target,
                                               EdgeConfigDocument.Ctr ctr) {
        if (currency == null || target == null) {
            return Optional.empty();
        }
        BigDecimal major = BigDecimal.valueOf(amountMinor).movePointLeft(digits(currency));
        if (currency.equals(target)) {
            return Optional.of(major);
        }
        BigDecimal inBase;
        if (currency.equals(ctr.fxBase())) {
            inBase = major;
        } else {
            BigDecimal r = ctr.rates().get(currency);
            if (r == null || r.signum() == 0) {
                return Optional.empty();
            }
            inBase = major.multiply(r);
        }
        if (target.equals(ctr.fxBase())) {
            return Optional.of(inBase);
        }
        BigDecimal t = ctr.rates().get(target);
        if (t == null || t.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(inBase.divide(t, MathContext.DECIMAL128));
    }

    /** ISO 4217 minor-unit digits (2 when the JDK does not know the code). */
    public static int digits(String currency) {
        try {
            int d = Currency.getInstance(currency).getDefaultFractionDigits();
            return d < 0 ? 0 : d;
        } catch (IllegalArgumentException | NullPointerException e) {
            return 2;
        }
    }
}
