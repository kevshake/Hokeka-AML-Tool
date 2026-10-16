package com.posgateway.aml.exception;

import com.posgateway.aml.controller.risk.PaymentBlacklistController;
import com.posgateway.aml.service.security.PaymentBlacklistAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** ResponseStatusException keeps its status (409 duplicate blacklist entry) instead of becoming 500. */
class ResponseStatusExceptionHandlingTest {

    @Test
    void conflictIsAnsweredAs409() {
        var response = new GlobalExceptionHandler(mock(com.posgateway.aml.service.admin.RuntimeErrorService.class)).handleResponseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT, "Blacklist entry already exists: 5"),
                new MockHttpServletRequest("POST", "/api/v1/blacklist"));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getMessage()).contains("already exists");
    }

    @Test
    void duplicateBlacklistEntryBecomesConflict() {
        PaymentBlacklistAdminService service = mock(PaymentBlacklistAdminService.class);
        when(service.create(any(), any())).thenThrow(new IllegalStateException("Blacklist entry already exists: 5"));
        PaymentBlacklistController controller = new PaymentBlacklistController(service);

        assertThatThrownBy(() -> controller.create(
                new PaymentBlacklistAdminService.CreateRequest("ip", "1.1.1.1", "r", null, null, null),
                new MockHttpServletRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }
}
