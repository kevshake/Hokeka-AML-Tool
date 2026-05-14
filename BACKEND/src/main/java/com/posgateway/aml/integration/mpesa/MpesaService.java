package com.posgateway.aml.integration.mpesa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.billing.PaymentAttempt;
import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.repository.InvoiceRepository;
import com.posgateway.aml.repository.PaymentAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for interacting with the Safaricom Daraja M-Pesa API.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Obtain and cache an OAuth2 access token from Daraja.</li>
 *   <li>Initiate an STK Push (Lipa Na M-Pesa Online) request.</li>
 *   <li>Process the asynchronous Daraja callback and update the invoice.</li>
 * </ul>
 *
 * <p>The token is cached in-memory for 55 minutes; Daraja issues tokens valid
 * for 3 600 s (1 hour). This avoids an extra network call on every payment.
 */
@Service
public class MpesaService {

    private static final Logger log = LoggerFactory.getLogger(MpesaService.class);
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final long TOKEN_CACHE_MILLIS = 55L * 60 * 1_000; // 55 minutes

    private final MpesaProperties props;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final InvoiceRepository invoiceRepository;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    // ─── Token cache ─────────────────────────────────────────────────────────
    private volatile String cachedToken = null;
    private volatile long tokenFetchedAt = 0L;

    public MpesaService(MpesaProperties props,
                        PaymentAttemptRepository paymentAttemptRepository,
                        InvoiceRepository invoiceRepository,
                        ObjectMapper objectMapper,
                        WebClient.Builder webClientBuilder) {
        this.props = props;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.invoiceRepository = invoiceRepository;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
    }

    // ─── OAuth2 token ─────────────────────────────────────────────────────────

    /**
     * Returns a valid Daraja access token, reusing the cached one if still fresh.
     */
    String getAccessToken() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && (now - tokenFetchedAt) < TOKEN_CACHE_MILLIS) {
            return cachedToken;
        }

        String credentials = props.getConsumerKey() + ":" + props.getConsumerSecret();
        String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        try {
            String body = webClient.get()
                    .uri(props.getBaseUrl() + "/oauth/v1/generate?grant_type=client_credentials")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(body);
            String token = root.path("access_token").asText();
            if (token.isBlank()) {
                throw new IllegalStateException("Daraja returned empty access_token");
            }
            cachedToken = token;
            tokenFetchedAt = System.currentTimeMillis();
            log.debug("Daraja access token refreshed successfully");
            return token;

        } catch (WebClientResponseException e) {
            log.error("Failed to obtain Daraja access token: HTTP {} — {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("M-Pesa authentication failed", e);
        } catch (Exception e) {
            log.error("Failed to obtain Daraja access token", e);
            throw new RuntimeException("M-Pesa authentication failed", e);
        }
    }

    // ─── STK Push ─────────────────────────────────────────────────────────────

    /**
     * Initiates a Lipa Na M-Pesa Online (STK Push) request.
     *
     * @param phoneNumber      Payer's phone (will be normalized to 254XXXXXXXXX)
     * @param amount           Amount to collect (must be a whole KES amount — decimals ignored)
     * @param invoiceId        Used as part of the account reference
     * @param accountReference Short label shown on the payer's phone (max 12 chars recommended)
     * @return parsed STK push response from Daraja
     */
    public MpesaStkResponse initiateSTKPush(String phoneNumber, BigDecimal amount,
                                             Long invoiceId, String accountReference) {
        String token = getAccessToken();
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        String normalizedPhone = normalizePhone(phoneNumber);
        String password = buildPassword(timestamp);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("BusinessShortCode", props.getShortCode());
        requestBody.put("Password", password);
        requestBody.put("Timestamp", timestamp);
        requestBody.put("TransactionType", "CustomerPayBillOnline");
        requestBody.put("Amount", amount.intValue());
        requestBody.put("PartyA", normalizedPhone);
        requestBody.put("PartyB", props.getShortCode());
        requestBody.put("PhoneNumber", normalizedPhone);
        requestBody.put("CallBackURL", props.getCallbackUrl());
        requestBody.put("AccountReference", accountReference != null ? accountReference : "INV-" + invoiceId);
        requestBody.put("TransactionDesc", "Invoice payment");

        try {
            String responseBody = webClient.post()
                    .uri(props.getBaseUrl() + "/mpesa/stkpush/v1/processrequest")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseBody);
            MpesaStkResponse response = new MpesaStkResponse(
                    root.path("MerchantRequestID").asText(null),
                    root.path("CheckoutRequestID").asText(null),
                    root.path("ResponseCode").asText(null),
                    root.path("ResponseDescription").asText(null),
                    root.path("CustomerMessage").asText(null)
            );

            if (!response.isSuccess()) {
                log.warn("Daraja STK push returned non-zero ResponseCode={} for invoice={}: {}",
                        response.getResponseCode(), invoiceId, response.getResponseDescription());
            } else {
                log.info("STK push initiated for invoice={}, checkoutRequestId={}", invoiceId, response.getCheckoutRequestId());
            }

            return response;

        } catch (WebClientResponseException e) {
            log.error("Daraja STK push HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("M-Pesa STK push failed", e);
        } catch (Exception e) {
            log.error("Daraja STK push error for invoice {}", invoiceId, e);
            throw new RuntimeException("M-Pesa STK push failed", e);
        }
    }

    // ─── Callback processing ──────────────────────────────────────────────────

    /**
     * Processes the asynchronous Daraja callback.
     *
     * <p>Safaricom will POST a body shaped like:
     * <pre>
     * {
     *   "Body": {
     *     "stkCallback": {
     *       "MerchantRequestID": "...",
     *       "CheckoutRequestID": "...",
     *       "ResultCode": 0,
     *       "ResultDesc": "The service request is processed successfully.",
     *       "CallbackMetadata": {
     *         "Item": [
     *           { "Name": "Amount", "Value": 1.0 },
     *           { "Name": "MpesaReceiptNumber", "Value": "ABC123" },
     *           { "Name": "TransactionDate", "Value": 20240101120000 }
     *         ]
     *       }
     *     }
     *   }
     * }
     * </pre>
     */
    public void processCallback(Map<String, Object> callbackBody) {
        try {
            JsonNode root = objectMapper.valueToTree(callbackBody);
            JsonNode stkCallback = root.path("Body").path("stkCallback");

            String checkoutRequestId = stkCallback.path("CheckoutRequestID").asText(null);
            int resultCode = stkCallback.path("ResultCode").asInt(-1);
            String resultDesc = stkCallback.path("ResultDesc").asText(null);

            if (checkoutRequestId == null || checkoutRequestId.isBlank()) {
                log.warn("Daraja callback missing CheckoutRequestID — ignoring");
                return;
            }

            Optional<PaymentAttempt> optAttempt = paymentAttemptRepository
                    .findByMpesaCheckoutRequestId(checkoutRequestId);

            if (optAttempt.isEmpty()) {
                log.warn("No payment attempt found for CheckoutRequestID={}", checkoutRequestId);
                return;
            }

            PaymentAttempt attempt = optAttempt.get();
            attempt.setResultCode(String.valueOf(resultCode));
            attempt.setResultDescription(resultDesc);
            attempt.setCompletedAt(OffsetDateTime.now());

            if (resultCode == 0) {
                // Extract metadata
                JsonNode items = stkCallback.path("CallbackMetadata").path("Item");
                String mpesaReceiptNumber = null;
                for (JsonNode item : items) {
                    String name = item.path("Name").asText("");
                    if ("MpesaReceiptNumber".equals(name)) {
                        mpesaReceiptNumber = item.path("Value").asText(null);
                    }
                }
                attempt.setStatus("SUCCESS");
                attempt.setMpesaTransactionId(mpesaReceiptNumber);

                // Mark invoice as PAID
                Optional<Invoice> optInvoice = invoiceRepository.findById(attempt.getInvoiceId());
                if (optInvoice.isPresent()) {
                    Invoice invoice = optInvoice.get();
                    invoice.markAsPaid(mpesaReceiptNumber, attempt.getAmount());
                    invoice.setPaymentMethod("MPESA");
                    invoiceRepository.save(invoice);
                    log.info("Invoice {} marked PAID via M-Pesa receipt {}", attempt.getInvoiceId(), mpesaReceiptNumber);
                } else {
                    log.warn("Invoice {} not found when processing successful M-Pesa callback", attempt.getInvoiceId());
                }

            } else if (resultCode == 1032) {
                // 1032 = user cancelled
                attempt.setStatus("CANCELLED");
                log.info("STK push cancelled by user for checkoutRequestId={}", checkoutRequestId);
            } else {
                attempt.setStatus("FAILED");
                log.warn("STK push failed for checkoutRequestId={}, resultCode={}, desc={}",
                        checkoutRequestId, resultCode, resultDesc);
            }

            paymentAttemptRepository.save(attempt);

        } catch (Exception e) {
            log.error("Error processing Daraja callback", e);
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Normalizes a Kenyan phone number to the 254XXXXXXXXX format required by Daraja.
     * Accepts 07XXXXXXXX, 7XXXXXXXX, +2547XXXXXXXX, 2547XXXXXXXX.
     */
    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String cleaned = phone.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("254")) {
            return cleaned;
        }
        if (cleaned.startsWith("0") && cleaned.length() == 10) {
            return "254" + cleaned.substring(1);
        }
        if (cleaned.length() == 9) {
            return "254" + cleaned;
        }
        return cleaned; // fall-through; Daraja will validate
    }

    /**
     * Builds the Daraja password: Base64(shortCode + passkey + timestamp).
     */
    private String buildPassword(String timestamp) {
        String raw = props.getShortCode() + props.getPasskey() + timestamp;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
