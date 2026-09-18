package com.posgateway.aml.integration.cbk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CbkGdiClientTest {

    @Test
    void retainsActualSuccessfulHttpStatusAndRequestNumber() {
        ExchangeFunction exchange = request -> Mono.just(ClientResponse.create(HttpStatus.ACCEPTED)
                .header("Content-Type", "application/json")
                .body("{\"RequestNo\":\"CBK-202\"}")
                .build());
        CbkGdiClient client = client(exchange);

        CbkSubmissionResult result = client.submit(context(), "/endpoint", "{}", 4);

        assertTrue(result.isSuccess());
        assertEquals(202, result.getHttpStatus());
        assertEquals("CBK-202", result.getRequestId());
        assertEquals(4, result.getSourceRecordCount());
    }

    @Test
    void transportExceptionAndFallbackRetainRegulatorStatusAndBody() {
        ExchangeFunction exchange = request -> Mono.just(ClientResponse.create(HttpStatus.UNPROCESSABLE_ENTITY)
                .header("Content-Type", "application/json")
                .body("{\"error\":\"invalid merchant account\"}")
                .build());
        CbkGdiClient client = client(exchange);

        CbkTokenService.CbkGdiException exception = assertThrows(
                CbkTokenService.CbkGdiException.class,
                () -> client.submit(context(), "/endpoint", "{}", 3));
        assertEquals(422, exception.getHttpStatus());
        assertTrue(exception.getResponseBody().contains("invalid merchant account"));
        assertEquals(3, exception.getSourceRecordCount());

        CbkSubmissionResult fallback = client.buildFallback(
                context(), "MERCHANT_TRANSACTIONS", new RuntimeException(exception));
        assertFalse(fallback.isSuccess());
        assertEquals(422, fallback.getHttpStatus());
        assertTrue(fallback.getBody().contains("invalid merchant account"));
        assertEquals(3, fallback.getSourceRecordCount());
    }

    private CbkGdiClient client(ExchangeFunction exchange) {
        CbkProperties properties = new CbkProperties();
        properties.setReadTimeoutMs(1_000);
        CbkTokenService tokenService = mock(CbkTokenService.class);
        when(tokenService.getToken(7L, "client", "secret", false)).thenReturn("token");
        WebClient webClient = WebClient.builder().exchangeFunction(exchange).build();
        return new CbkGdiClient(properties, tokenService, new ObjectMapper(), webClient);
    }

    private PspCbkContext context() {
        return new PspCbkContext(7L, "BANK7", "client", "secret", false);
    }
}
