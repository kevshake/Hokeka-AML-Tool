package com.posgateway.aml.integration.cbk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.integration.cbk.records.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for submitting CBK GDI regulatory reports.
 *
 * <p>Each of the 17 CBK endpoint methods follows the same flow:
 * <ol>
 *   <li>Resolve Bearer token via {@link CbkTokenService}.</li>
 *   <li>Build the JSON envelope via {@link CbkEnvelope}.</li>
 *   <li>Wrap the body in multipart/mixed via {@link CbkMultipartWrapper}.</li>
 *   <li>POST to {@code https://{baseUrl}{postPrefix}{endpointPath}} with the required headers.</li>
 *   <li>Parse {@code RequestNo} from the 200 response.</li>
 * </ol>
 *
 * <p>All public submit methods are protected by the {@code cbkGdi} Resilience4j
 * circuit-breaker and retry instances configured in application.properties.
 * Fallbacks return a failure-status {@link CbkSubmissionResult} — nothing propagates
 * past the breaker.
 */
@Component
public class CbkGdiClient {

    private static final Logger log = LoggerFactory.getLogger(CbkGdiClient.class);
    private static final String CB_NAME = "cbkGdi";

    // Fixed headers matching the legacy RestAssured client exactly
    private static final String USER_AGENT = "Apache-HttpClient/4.5.5 (Java/17.0.12)";
    private static final String ACCEPT_ENCODING = "gzip,deflate";
    private static final String CONNECTION = "Keep-Alive";
    private static final String REQUEST_NO_FIELD = "RequestNo";

    // ---- CBK endpoint paths (verbatim from inventory) ----
    private static final String PATH_SENIOR_MANAGEMENT =
            "/api/v1/flows/rest/CBK_API_SENIO_MNGT_SCHED_PAREN/1.0/CBK_API_SENIOR_MNGT_SCHEDULE_PARENT";
    private static final String PATH_DIRECTORS =
            "/api/v1/flows/rest/CBK_API_SCHED_OF_DIR_PAREN/1.0/CBK_API_SCHED_OF_DIR_PARENT";
    private static final String PATH_TRUSTEES =
            "/api/v1/flows/rest/CBK_API_SCHED_OF_TRUST_PAREN/1.0/CBK_API_SCHED_OF_TRUSTEES_PARENT";
    private static final String PATH_SHAREHOLDERS =
            "/api/v1/flows/rest/CBK_API_SCHE_OF_SHAR_HLDR_PARE/1.0/CBK_API_SCHED_OF_SHARE_HLDRS_PARENT";
    private static final String PATH_CUSTOMER_COMPLAINTS =
            "/api/v1/flows/rest/API_PSPSCHEDULECUSTC/1.0/PSPs_Sched_CustComplnts_and_Remedials";
    private static final String PATH_CYBER_INCIDENTS =
            "/api/v1/flows/rest/API_PSPCYBERSECURITYSYNC/1.0/PSPs_Cybersec_Incident_Record";
    private static final String PATH_FRAUD_INCIDENTS =
            "/api/v1/flows/rest/API_INCIDENTSINFOSYN/1.0/PSPs_Fraud_Theft_Robbery_Incidents";
    private static final String PATH_SYSTEM_STABILITY =
            "/api/v1/flows/rest/API_SCHDLESYSTEMSTBSVCINTERPSYNC/1.0/PSPs_Sched_SysStability_and_Srvc_Intrpt";
    private static final String PATH_SYSTEM_ACTIVITY =
            "/api/v1/flows/rest/API_SYSTEMACTIVITYSYNC/1.0/PSPs_System_Activity";
    private static final String PATH_PRODUCTS =
            "/api/v1/flows/rest/API_MOBILEPSPPRODUCTSSYNC/1.0/PSPs_Products";
    private static final String PATH_TRUST_ACCOUNT =
            "/api/v1/flows/rest/API_TRUSTACCOUNTSYNC/1.0/PSPs_Trust_acct";
    private static final String PATH_CARD_BRANDS =
            "/api/v1/flows/rest/CBK_API_PAYEM_GATEW_CARD_BRAND/1.0/PAYMENTGATEWAYCARDBRANDS";
    private static final String PATH_BILLING_TEMPLATE =
            "/api/v1/flows/rest/API_PAYMENTGATEWAYBITEMPSY/1.0/PSPs_PayGtwy_Billing_Temp";
    private static final String PATH_TRANSACTION_DETAILS =
            "/api/v1/flows/rest/API_PAYGATWAYTRXDETAILSSYNC/1.0/PSPs_PayGtwy_Txn_Details";
    private static final String PATH_TRANSACTION_TARIFFS =
            "/api/v1/flows/rest/API_PAYMENTGATEWAYSYNC/1.0/PSPs_PayGtwy_Txn_Tarrifs";
    private static final String PATH_MERCHANT_TRANSACTIONS =
            "/api/v1/flows/rest/API_PSPMERCHANTTRANS/1.0/PSPs_PayGtwy_Merch_Txns";
    private static final String PATH_FAILED_TRANSACTIONS =
            "/api/v1/flows/rest/API_FAILEDREJECTEDTRXSYNC/1.0/PSPs_PayGtwy_Failed_Rjected_Txns";

    private final CbkProperties properties;
    private final CbkTokenService tokenService;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public CbkGdiClient(CbkProperties properties, CbkTokenService tokenService, ObjectMapper objectMapper) {
        this(properties, tokenService, objectMapper, buildWebClient(properties));
    }

    CbkGdiClient(CbkProperties properties, CbkTokenService tokenService,
                 ObjectMapper objectMapper, WebClient webClient) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.webClient = webClient;
    }

    private static WebClient buildWebClient(CbkProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)));

        // No baseUrl baked in — each call resolves its base URL from the PSP context's
        // liveEffective flag, so two PSPs configured for different environments can run
        // concurrently against different CBK hosts.
        return WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .build();
    }

    // ============================================================================================
    // Public endpoint methods — one per CBK API endpoint
    // ============================================================================================

    /** Endpoint #1 — Senior Management Schedule. Annual, Jan 5. Today's date. */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackSeniorManagement")
    @Retry(name = CB_NAME)
    public CbkSubmissionResult submitSeniorManagement(PspCbkContext ctx, List<SeniorManagementRecord> records) {
        String json = CbkEnvelope.forToday(objectMapper, ctx.getInstitutionCode(), "SENIOR_MNGT_SCHEDULE", records);
        return submit(ctx, PATH_SENIOR_MANAGEMENT, json, records.size());
    }

    /** Endpoint #2 — Schedule of Directors. Annual, Jan 5. Today's date. */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackDirectors")
    @Retry(name = CB_NAME)
    public CbkSubmissionResult submitDirectors(PspCbkContext ctx, List<DirectorRecord> records) {
        String json = CbkEnvelope.forToday(objectMapper, ctx.getInstitutionCode(), "SCHED_OF_DIR", records);
        return submit(ctx, PATH_DIRECTORS, json, records.size());
    }

    /** Endpoint #3 — Schedule of Trustees. Annual, Jan 5. Today's date. */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackTrustees")
    @Retry(name = CB_NAME)
    public CbkSubmissionResult submitTrustees(PspCbkContext ctx, List<TrusteeRecord> records) {
        String json = CbkEnvelope.forToday(objectMapper, ctx.getInstitutionCode(), "SCHED_OF_TRUSTEES", records);
        return submit(ctx, PATH_TRUSTEES, json, records.size());
    }

    /** Endpoint #4 — Schedule of Shareholders. Annual, Jan 4. Today's date. */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackShareholders")
    @Retry(name = CB_NAME)
    public CbkSubmissionResult submitShareholders(PspCbkContext ctx, List<ShareholderRecord> records) {
        String json = CbkEnvelope.forToday(objectMapper, ctx.getInstitutionCode(), "SCHED_OF_SHARE_HLDRS", records);
        return submit(ctx, PATH_SHAREHOLDERS, json, records.size());
    }

    /** Endpoint #5 — Customer Complaints. Monthly, day 3. Yesterday's date. */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackCustomerComplaints")
    @Retry(name = CB_NAME)
    public CbkSubmissionResult submitCustomerComplaints(PspCbkContext ctx, List<CustomerComplaintRecord> records) {
        String json = CbkEnvelope.forYesterday(objectMapper, ctx.getInstitutionCode(), "PSP_CUTOMER_COMPLAINTS", records);
        return submit(ctx, PATH_CUSTOMER_COMPLAINTS, json, records.size());
    }

    /** Endpoint #6 — Cybersecurity Incidents. Daily. Yesterday's date. */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackCyberIncidents")
    @Retry(name = CB_NAME)
    public CbkSubmissionResult submitCyberIncidents(PspCbkContext ctx, List<CyberIncidentRecord> records) {
        String json = CbkEnvelope.forYesterday(objectMapper, ctx.getInstitutionCode(), "PSP_CYBERSECURITY_INCIDENT_RECORD", records);
        return submit(ctx, PATH_CYBER_INCIDENTS, json, records.size());
    }

    /** Endpoint #7 — Fraud / Theft / Robbery Incidents. Daily. Yesterday's date. */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackFraudIncidents")
    @Retry(name = CB_NAME)
    public CbkSubmissionResult submitFraudIncidents(PspCbkContext ctx, List<FraudIncidentRecord> records) {
        String json = CbkEnvelope.forYesterday(objectMapper, ctx.getInstitutionCode(), "INCIDENTS_DATA", records);
        return submit(ctx, PATH_FRAUD_INCIDENTS, json, records.size());
    }

    /** Endpoint #8 — System Stability / Service Interruption. Daily. Yesterday's date. */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackSystemStability")
    @Retry(name = CB_NAME)
    public CbkSubmissionResult submitSystemStability(PspCbkContext ctx, List<SystemStabilityRecord> records) {
        String json = CbkEnvelope.forYesterday(objectMapper, ctx.getInstitutionCode(), "SCH_SY_STABIL_SRVCE_INT", records);
        return submit(ctx, PATH_SYSTEM_STABILITY, json, records.size());
    }

    /** Endpoint #9 — System Activity (24 records/day, one per hour). Daily. Yesterday's date. */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackSystemActivity")
    @Retry(name = CB_NAME)
    public CbkSubmissionResult submitSystemActivity(PspCbkContext ctx, List<SystemActivityRecord> records) {
        String json = CbkEnvelope.forYesterday(objectMapper, ctx.getInstitutionCode(), "SYSTEM_ACTIVITY_INFO", records);
        return submit(ctx, PATH_SYSTEM_ACTIVITY, json, records.size());
    }

    /** Endpoint #10 — Products Info. Monthly, day 1. Yesterday's date. */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackProducts")
    @Retry(name = CB_NAME)
    public CbkSubmissionResult submitProducts(PspCbkContext ctx, List<ProductRecord> records) {
        String json = CbkEnvelope.forYesterday(objectMapper, ctx.getInstitutionCode(), "PSP_PRODUCTS_INFO", records);
        return submit(ctx, PATH_PRODUCTS, json, records.size());
    }

    /** Endpoint #11 — Trust Accounts. Daily. Yesterday's date. */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackTrustAccounts")
    @Retry(name = CB_NAME)
    public CbkSubmissionResult submitTrustAccounts(PspCbkContext ctx, List<TrustAccountRecord> records) {
        String json = CbkEnvelope.forYesterday(objectMapper, ctx.getInstitutionCode(), "TRUSTACCOUNT_DATA", records);
        return submit(ctx, PATH_TRUST_ACCOUNT, json, records.size());
    }

    /** Endpoint #12 — Card Brands. Monthly, day 2. Yesterday's date. */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackCardBrands")
    @Retry(name = CB_NAME)
    public CbkSubmissionResult submitCardBrands(PspCbkContext ctx, List<CardBrandRecord> records) {
        String json = CbkEnvelope.forYesterday(objectMapper, ctx.getInstitutionCode(), "PYMT_GW_CARD_BRANDS", records);
        return submit(ctx, PATH_CARD_BRANDS, json, records.size());
    }

    /** Endpoint #13 — Billing Template. Daily. Yesterday's date. */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackBillingTemplate")
    @Retry(name = CB_NAME)
    public CbkSubmissionResult submitBillingTemplate(PspCbkContext ctx, List<BillingTemplateRecord> records) {
        String json = CbkEnvelope.forYesterday(objectMapper, ctx.getInstitutionCode(), "PAY_GTWAY_BILL_TEMP", records);
        return submit(ctx, PATH_BILLING_TEMPLATE, json, records.size());
    }

    /** Endpoint #14 — Transaction Details. Monthly. Yesterday's date. */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackTransactionDetails")
    @Retry(name = CB_NAME)
    public CbkSubmissionResult submitTransactionDetails(PspCbkContext ctx, List<TransactionDetailRecord> records) {
        String json = CbkEnvelope.forYesterday(objectMapper, ctx.getInstitutionCode(), "PAYMENT_GATEWAY_TRANSACTIONS_DETAILS", records);
        return submit(ctx, PATH_TRANSACTION_DETAILS, json, records.size());
    }

    /** Endpoint #15 — Transaction Tariffs. Monthly. Yesterday's date. */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackTransactionTariffs")
    @Retry(name = CB_NAME)
    public CbkSubmissionResult submitTransactionTariffs(PspCbkContext ctx, List<TransactionTariffRecord> records) {
        String json = CbkEnvelope.forYesterday(objectMapper, ctx.getInstitutionCode(), "PAYMENT_GATEWAY_TARIFFS", records);
        return submit(ctx, PATH_TRANSACTION_TARIFFS, json, records.size());
    }

    /** Endpoint #16 — Merchant Transactions (success). Daily. Yesterday's date. */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackMerchantTransactions")
    @Retry(name = CB_NAME)
    public CbkSubmissionResult submitMerchantTransactions(PspCbkContext ctx, List<MerchantTransactionRecord> records) {
        String json = CbkEnvelope.forYesterday(objectMapper, ctx.getInstitutionCode(), "MERCHANT_STLMNT_ACCT_DATA", records);
        return submit(ctx, PATH_MERCHANT_TRANSACTIONS, json, records.size());
    }

    /** Endpoint #17 — Failed / Rejected Transactions. Daily. Yesterday's date. */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackFailedTransactions")
    @Retry(name = CB_NAME)
    public CbkSubmissionResult submitFailedTransactions(PspCbkContext ctx, List<FailedTransactionRecord> records) {
        String json = CbkEnvelope.forYesterday(objectMapper, ctx.getInstitutionCode(), "FAILED_REJECTED_TRX_INFO", records);
        return submit(ctx, PATH_FAILED_TRANSACTIONS, json, records.size());
    }

    // ============================================================================================
    // Generic submit
    // ============================================================================================

    /**
     * Core HTTP submission: wraps JSON in multipart, attaches headers, POSTs, parses result.
     *
     * <p>Intentionally package-private so unit tests can verify envelope + multipart without
     * needing to call a named endpoint method.
     */
    CbkSubmissionResult submit(PspCbkContext ctx, String endpointPath, String jsonBody) {
        return submit(ctx, endpointPath, jsonBody, 0);
    }

    CbkSubmissionResult submit(PspCbkContext ctx, String endpointPath, String jsonBody,
                               int sourceRecordCount) {
        long start = System.currentTimeMillis();

        String token = tokenService.getToken(
                ctx.getPspId(), ctx.getClientId(), ctx.getClientSecret(), ctx.isLiveEffective());
        CbkMultipartWrapper.Result mp = CbkMultipartWrapper.wrap(jsonBody);

        // Per-PSP routing: use the context's liveEffective flag to pick host + URL prefix.
        String fullUrl = properties.baseUrlFor(ctx.isLiveEffective())
                + properties.postPrefixFor(ctx.isLiveEffective())
                + endpointPath;

        log.debug("CBK GDI POST PSP={} env={} url={}",
                ctx.getPspId(), ctx.isLiveEffective() ? "LIVE" : "preprod", fullUrl);

        try {
            ResponseEntity<String> response = webClient.post()
                    .uri(fullUrl)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", mp.getContentTypeHeaderValue())
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Encoding", ACCEPT_ENCODING)
                    .header("Connection", CONNECTION)
                    .header("Host", properties.hostFor(ctx.isLiveEffective()))
                    // Stable idempotency key so a @Retry after an ambiguous ack (timeout) does
                    // not file the same regulatory report twice, if CBK honours the header.
                    .header("X-Idempotency-Key", idempotencyKey(ctx.getPspId(), endpointPath, jsonBody))
                    .bodyValue(mp.getBody())
                    .retrieve()
                    .toEntity(String.class)
                    .block(Duration.ofMillis(properties.getReadTimeoutMs() + 1000L));

            long durationMs = System.currentTimeMillis() - start;
            if (response == null) {
                throw new CbkTokenService.CbkGdiException(
                        "CBK returned no HTTP response", -1, null, durationMs,
                        sourceRecordCount, null);
            }
            int responseStatus = response.getStatusCode().value();
            String responseBody = response.getBody();

            String requestNo = parseRequestNo(responseBody);
            if (requestNo != null) {
                log.info("CBK GDI success PSP={} path={} RequestNo={} status={} ({}ms)",
                        ctx.getPspId(), fullUrl, requestNo, responseStatus, durationMs);
                return CbkSubmissionResult.ok(
                        requestNo, responseStatus, responseBody, durationMs, sourceRecordCount);
            } else {
                // 200 but no RequestNo — treat as failure
                log.warn("CBK GDI: status {} response but no RequestNo for PSP={} path={}",
                        responseStatus, ctx.getPspId(), fullUrl);
                return CbkSubmissionResult.failure(
                        "No RequestNo in response", responseStatus, responseBody,
                        durationMs, sourceRecordCount);
            }

        } catch (WebClientResponseException e) {
            long durationMs = System.currentTimeMillis() - start;
            log.warn("CBK GDI HTTP error PSP={} path={} status={}: {}",
                    ctx.getPspId(), fullUrl, e.getStatusCode(), e.getMessage());
            // Re-throw so Resilience4j @Retry can decide whether to retry
            throw new CbkTokenService.CbkGdiException(
                    "HTTP " + e.getStatusCode().value() + " from CBK: " + e.getResponseBodyAsString(),
                    e.getStatusCode().value(), e.getResponseBodyAsString(), durationMs,
                    sourceRecordCount, e);
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            log.warn("CBK GDI error PSP={} path={}: {}", ctx.getPspId(), fullUrl, e.getMessage());
            if (e instanceof CbkTokenService.CbkGdiException transportException) {
                throw transportException;
            }
            throw new CbkTokenService.CbkGdiException(
                    "CBK submission failed: " + e.getMessage(), -1, null, durationMs,
                    sourceRecordCount, e);
        }
    }

    private String parseRequestNo(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode field = node.get(REQUEST_NO_FIELD);
            return (field != null && !field.isNull()) ? field.asText() : null;
        } catch (Exception e) {
            log.debug("Could not parse RequestNo from response body: {}", e.getMessage());
            return null;
        }
    }

    // ============================================================================================
    // Fallback methods (one per circuit-breaker-annotated method)
    // ============================================================================================

    @SuppressWarnings("unused")
    private CbkSubmissionResult fallbackSeniorManagement(PspCbkContext ctx, List<SeniorManagementRecord> records, Throwable t) {
        return buildFallback(ctx, "SENIOR_MANAGEMENT", records.size(), t);
    }

    @SuppressWarnings("unused")
    private CbkSubmissionResult fallbackDirectors(PspCbkContext ctx, List<DirectorRecord> records, Throwable t) {
        return buildFallback(ctx, "DIRECTORS", records.size(), t);
    }

    @SuppressWarnings("unused")
    private CbkSubmissionResult fallbackTrustees(PspCbkContext ctx, List<TrusteeRecord> records, Throwable t) {
        return buildFallback(ctx, "TRUSTEES", records.size(), t);
    }

    @SuppressWarnings("unused")
    private CbkSubmissionResult fallbackShareholders(PspCbkContext ctx, List<ShareholderRecord> records, Throwable t) {
        return buildFallback(ctx, "SHAREHOLDERS", records.size(), t);
    }

    @SuppressWarnings("unused")
    private CbkSubmissionResult fallbackCustomerComplaints(PspCbkContext ctx, List<CustomerComplaintRecord> records, Throwable t) {
        return buildFallback(ctx, "CUSTOMER_COMPLAINTS", records.size(), t);
    }

    @SuppressWarnings("unused")
    private CbkSubmissionResult fallbackCyberIncidents(PspCbkContext ctx, List<CyberIncidentRecord> records, Throwable t) {
        return buildFallback(ctx, "CYBER_INCIDENTS", records.size(), t);
    }

    @SuppressWarnings("unused")
    private CbkSubmissionResult fallbackFraudIncidents(PspCbkContext ctx, List<FraudIncidentRecord> records, Throwable t) {
        return buildFallback(ctx, "FRAUD_INCIDENTS", records.size(), t);
    }

    @SuppressWarnings("unused")
    private CbkSubmissionResult fallbackSystemStability(PspCbkContext ctx, List<SystemStabilityRecord> records, Throwable t) {
        return buildFallback(ctx, "SYSTEM_STABILITY", records.size(), t);
    }

    @SuppressWarnings("unused")
    private CbkSubmissionResult fallbackSystemActivity(PspCbkContext ctx, List<SystemActivityRecord> records, Throwable t) {
        return buildFallback(ctx, "SYSTEM_ACTIVITY", records.size(), t);
    }

    @SuppressWarnings("unused")
    private CbkSubmissionResult fallbackProducts(PspCbkContext ctx, List<ProductRecord> records, Throwable t) {
        return buildFallback(ctx, "PRODUCTS", records.size(), t);
    }

    @SuppressWarnings("unused")
    private CbkSubmissionResult fallbackTrustAccounts(PspCbkContext ctx, List<TrustAccountRecord> records, Throwable t) {
        return buildFallback(ctx, "TRUST_ACCOUNTS", records.size(), t);
    }

    @SuppressWarnings("unused")
    private CbkSubmissionResult fallbackCardBrands(PspCbkContext ctx, List<CardBrandRecord> records, Throwable t) {
        return buildFallback(ctx, "CARD_BRANDS", records.size(), t);
    }

    @SuppressWarnings("unused")
    private CbkSubmissionResult fallbackBillingTemplate(PspCbkContext ctx, List<BillingTemplateRecord> records, Throwable t) {
        return buildFallback(ctx, "BILLING_TEMPLATE", records.size(), t);
    }

    @SuppressWarnings("unused")
    private CbkSubmissionResult fallbackTransactionDetails(PspCbkContext ctx, List<TransactionDetailRecord> records, Throwable t) {
        return buildFallback(ctx, "TRANSACTION_DETAILS", records.size(), t);
    }

    @SuppressWarnings("unused")
    private CbkSubmissionResult fallbackTransactionTariffs(PspCbkContext ctx, List<TransactionTariffRecord> records, Throwable t) {
        return buildFallback(ctx, "TRANSACTION_TARIFFS", records.size(), t);
    }

    @SuppressWarnings("unused")
    private CbkSubmissionResult fallbackMerchantTransactions(PspCbkContext ctx, List<MerchantTransactionRecord> records, Throwable t) {
        return buildFallback(ctx, "MERCHANT_TRANSACTIONS", records.size(), t);
    }

    @SuppressWarnings("unused")
    private CbkSubmissionResult fallbackFailedTransactions(PspCbkContext ctx, List<FailedTransactionRecord> records, Throwable t) {
        return buildFallback(ctx, "FAILED_TRANSACTIONS", records.size(), t);
    }

    CbkSubmissionResult buildFallback(PspCbkContext ctx, String endpoint, Throwable t) {
        return buildFallback(ctx, endpoint, 0, t);
    }

    private CbkSubmissionResult buildFallback(PspCbkContext ctx, String endpoint,
                                              int sourceRecordCount, Throwable t) {
        log.warn("CBK GDI fallback engaged PSP={} endpoint={}: {}", ctx.getPspId(), endpoint,
                t != null ? t.getMessage() : "circuit open");
        CbkTokenService.CbkGdiException transportException = findTransportException(t);
        if (transportException != null) {
            return CbkSubmissionResult.failure(
                    "Circuit breaker open or retry exhausted for " + endpoint + ": "
                            + transportException.getMessage(),
                    transportException.getHttpStatus(),
                    transportException.getResponseBody(),
                    transportException.getDurationMs(),
                    transportException.getSourceRecordCount() > 0
                            ? transportException.getSourceRecordCount()
                            : sourceRecordCount);
        }
        return CbkSubmissionResult.failure(
                "Circuit breaker open or retry exhausted for " + endpoint + ": " +
                        (t != null ? t.getMessage() : "unknown"),
                0, null, 0, sourceRecordCount);
    }

    private CbkTokenService.CbkGdiException findTransportException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CbkTokenService.CbkGdiException transportException) {
                return transportException;
            }
            current = current.getCause();
        }
        return null;
    }

    /** Deterministic idempotency key for a submission (SHA-256 of pspId + endpoint + body). */
    private static String idempotencyKey(Long pspId, String endpointPath, String jsonBody) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((pspId + "|" + endpointPath + "|" + (jsonBody == null ? "" : jsonBody))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return pspId + "-" + Math.abs(java.util.Objects.hash(endpointPath, jsonBody));
        }
    }
}
