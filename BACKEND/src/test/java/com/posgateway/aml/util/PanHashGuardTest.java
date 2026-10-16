package com.posgateway.aml.util;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.TransactionEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Raw-PAN detection and its enforcement on the pan_hash entity setters. */
class PanHashGuardTest {

    private static final String VISA_TEST_PAN = "4111111111111111";
    private static final String SHA256_HEX = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @Test
    void detectsRawPanShapesWithAndWithoutSeparators() {
        assertThat(PanHashGuard.looksLikeRawPan(VISA_TEST_PAN)).isTrue();
        assertThat(PanHashGuard.looksLikeRawPan("4111 1111 1111 1111")).isTrue();
        assertThat(PanHashGuard.looksLikeRawPan("5500-0000-0000-0004")).isTrue();
        assertThat(PanHashGuard.looksLikeRawPan("1234567890123")).isTrue();       // 13 digits, non-Luhn
        assertThat(PanHashGuard.looksLikeRawPan("1234567890123456789")).isTrue(); // 19 digits
    }

    @Test
    void acceptsHashesTokensSentinelsAndShortOrLongNumbers() {
        assertThat(PanHashGuard.looksLikeRawPan(SHA256_HEX)).isFalse();
        assertThat(PanHashGuard.looksLikeRawPan("UNKNOWN")).isFalse();
        assertThat(PanHashGuard.looksLikeRawPan("tok_4111111111111111")).isFalse();
        assertThat(PanHashGuard.looksLikeRawPan("123456789012")).isFalse();      // 12 digits
        assertThat(PanHashGuard.looksLikeRawPan("12345678901234567890")).isFalse(); // 20 digits
        assertThat(PanHashGuard.looksLikeRawPan(null)).isFalse();
        assertThat(PanHashGuard.looksLikeRawPan("")).isFalse();
    }

    @Test
    void luhnCheck() {
        assertThat(PanHashGuard.isLuhnValid(VISA_TEST_PAN)).isTrue();
        assertThat(PanHashGuard.isLuhnValid("4111111111111112")).isFalse();
        assertThat(PanHashGuard.isLuhnValid("abc")).isFalse();
    }

    @Test
    void rejectionMessageNeverContainsTheValue() {
        assertThatThrownBy(() -> PanHashGuard.requireNotRawPan(VISA_TEST_PAN, "transactions.pan_hash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transactions.pan_hash")
                .hasMessageContaining("passes the Luhn check")
                .hasMessageNotContaining("4111");
    }

    @Test
    void entitySettersRejectRawPanButAcceptHashes() {
        TransactionEntity txn = new TransactionEntity();
        assertThatThrownBy(() -> txn.setPanHash(VISA_TEST_PAN)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> txn.setPanHash(SHA256_HEX)).doesNotThrowAnyException();
        assertThat(txn.getPanHash()).isEqualTo(SHA256_HEX);
        assertThatCode(() -> txn.setPanHash(null)).doesNotThrowAnyException();

        // alerts.pan_hash is retired (V264); the alert's txnRef drops card-shaped values instead.
        Alert alert = new Alert();
        alert.setTxnRef("5500 0000 0000 0004");
        assertThat(alert.getTxnRef()).isNull();
        alert.setTxnRef(VISA_TEST_PAN);
        assertThat(alert.getTxnRef()).isNull();
        alert.setTxnRef("ORD-2026-0001");
        assertThat(alert.getTxnRef()).isEqualTo("ORD-2026-0001");
    }
}
