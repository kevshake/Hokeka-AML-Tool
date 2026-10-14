package com.posgateway.aml.controller.marketing;

import com.posgateway.aml.dto.marketing.WebsiteContactRequest;
import com.posgateway.aml.dto.marketing.WebsiteContactResponse;
import com.posgateway.aml.service.marketing.WebsiteContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicWebsiteControllerTest {

    @Mock
    private WebsiteContactService websiteContactService;

    private PublicWebsiteController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicWebsiteController(websiteContactService);
    }

    @Test
    void submitContactReturnsAcceptedWithBody() {
        WebsiteContactRequest request = new WebsiteContactRequest();
        request.setFullName("Jordan Lee");
        request.setWorkEmail("jordan@bank.example");
        request.setMessage("Interested in edge + cloud dual-post architecture.");

        when(websiteContactService.submit(any(), any(), any()))
                .thenReturn(new WebsiteContactResponse(7L, "NEW", LocalDateTime.parse("2026-10-14T12:00:00")));

        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr("198.51.100.4");
        http.addHeader("User-Agent", "Mozilla/5.0");

        var result = controller.submitContact(request, http);

        assertEquals(202, result.getStatusCode().value());
        assertEquals(7L, result.getBody().getInquiryId());
        verify(websiteContactService).submit(eq(request), eq("198.51.100.4"), eq("Mozilla/5.0"));
    }
}
