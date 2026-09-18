package com.posgateway.aml.service.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.util.Map;

/** Thin HTTP client for a token-vault card gateway. */
@Component
public class CardPaymentGatewayClient {
    private final RestTemplate restTemplate;
    private final String gatewayUrl;

    public CardPaymentGatewayClient(RestTemplate restTemplate,
            @Value("${billing.card.gateway-url:}") String gatewayUrl) {
        this.restTemplate = restTemplate;
        this.gatewayUrl = gatewayUrl;
    }

    public ChargeResult charge(String tokenVaultRef, BigDecimal amount, String currency, String reference) {
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            return new ChargeResult(false, null, "Card gateway is not configured");
        }
        try {
            var response = restTemplate.postForEntity(gatewayUrl + "/charges",
                    Map.of("paymentMethodToken", tokenVaultRef, "amount", amount,
                            "currency", currency, "reference", reference),
                    Map.class);
            Map<?, ?> body = response.getBody();
            boolean approved = response.getStatusCode().is2xxSuccessful()
                    && body != null && Boolean.TRUE.equals(body.get("approved"));
            return new ChargeResult(approved,
                    body == null ? null : String.valueOf(body.get("transactionId")),
                    body == null ? null : String.valueOf(body.get("message")));
        } catch (Exception failure) {
            return new ChargeResult(false, null, failure.getMessage());
        }
    }

    public record ChargeResult(boolean approved, String transactionId, String message) {}
}
