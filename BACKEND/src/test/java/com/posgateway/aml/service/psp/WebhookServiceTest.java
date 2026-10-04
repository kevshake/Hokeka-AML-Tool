package com.posgateway.aml.service.psp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WebhookServiceTest {

    @Test
    void signatureIsStableKeyedAndBindsTheTimestamp() {
        String sig = WebhookService.sign("secret", 1000L, "{\"a\":1}");
        assertNotNull(sig);
        assertEquals(sig, WebhookService.sign("secret", 1000L, "{\"a\":1}"));
        assertNotEquals(sig, WebhookService.sign("other-secret", 1000L, "{\"a\":1}"));
        assertNotEquals(sig, WebhookService.sign("secret", 1001L, "{\"a\":1}"));
        assertNotEquals(sig, WebhookService.sign("secret", 1000L, "{\"a\":2}"));
        assertEquals(64, sig.length(), "hex HMAC-SHA256 is 64 chars");
    }

    @Test
    void noSecretYieldsNoSignature() {
        assertNull(WebhookService.sign(null, 1L, "{}"));
        assertNull(WebhookService.sign("", 1L, "{}"));
    }
}
