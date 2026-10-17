package com.posgateway.aml.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** PAN redaction of analyst free text (contract §2.3, §2.5). */
class PanRedactorTest {

    /** Verifies: Luhn-valid card numbers are redacted in any common spacing; other digits stay. */
    @Test
    void redactsLuhnValidCardNumbersOnly() {
        String text = "Card 4111 1111 1111 1111 and 5500-0000-0000-0004 used; ref 1234567890123 (not Luhn); "
                + "amount 2100000; phone 0712345678.";
        String out = PanRedactor.redact(text);
        assertThat(out).doesNotContain("4111 1111 1111 1111").doesNotContain("5500-0000-0000-0004");
        assertThat(out).contains("Card [REDACTED-PAN] and [REDACTED-PAN] used");
        assertThat(out).contains("1234567890123", "2100000", "0712345678");
        assertThat(PanRedactor.containsPan(text)).isTrue();
        assertThat(PanRedactor.containsPan(out)).isFalse();
    }

    /** Verifies: null, empty and digit-free text pass through unchanged. */
    @Test
    void leavesTextWithoutPansUnchanged() {
        assertThat(PanRedactor.redact(null)).isNull();
        assertThat(PanRedactor.redact("")).isEmpty();
        String plain = "Structuring below the threshold over three days.";
        assertThat(PanRedactor.redact(plain)).isSameAs(plain);
        assertThat(PanRedactor.redact("4111111111111111")).isEqualTo(PanRedactor.REDACTED);
    }
}
