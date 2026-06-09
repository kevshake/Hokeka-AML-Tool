package com.posgateway.aml.integration.verifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VerifiWebhookSignatureVerifierTest {

  private final VerifiWebhookSignatureVerifier verifier =
          new VerifiWebhookSignatureVerifier(new ObjectMapper());

  @Test
  void verifyApiKey_acceptsMatchingHeader() {
    assertTrue(verifier.verifyApiKey(
            Map.of("X-Api-Key", "secret-key"),
            "secret-key"));
  }

  @Test
  void verifyApiKey_rejectsMismatch() {
    assertFalse(verifier.verifyApiKey(
            Map.of("X-Api-Key", "wrong"),
            "secret-key"));
  }
}
