package com.posgateway.aml.service.marketing;

import com.posgateway.aml.dto.marketing.WebsiteContactRequest;
import com.posgateway.aml.dto.marketing.WebsiteContactResponse;
import com.posgateway.aml.entity.marketing.WebsiteContactInquiry;
import com.posgateway.aml.repository.marketing.WebsiteContactInquiryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebsiteContactServiceTest {

    @Mock
    private WebsiteContactInquiryRepository repository;

    private WebsiteContactService service;

    @BeforeEach
    void setUp() {
        service = new WebsiteContactService(repository);
    }

    @Test
    void submitPersistsNormalisedInquiry() {
        WebsiteContactRequest request = new WebsiteContactRequest();
        request.setFullName("  Ada   Lovelace ");
        request.setWorkEmail("Ada@Example.COM");
        request.setOrganization(" Analytical Engines ");
        request.setMessage("We need a private walkthrough of multi-asset AML.");
        request.setInquiryType("demo");

        when(repository.save(any(WebsiteContactInquiry.class))).thenAnswer(invocation -> {
            WebsiteContactInquiry row = invocation.getArgument(0);
            row.setId(42L);
            row.setCreatedAt(LocalDateTime.parse("2026-10-14T10:00:00"));
            return row;
        });

        WebsiteContactResponse response = service.submit(request, "203.0.113.10", "TestAgent/1.0");

        assertEquals(42L, response.getInquiryId());
        assertEquals("NEW", response.getStatus());
        assertNotNull(response.getReceivedAt());

        ArgumentCaptor<WebsiteContactInquiry> captor = ArgumentCaptor.forClass(WebsiteContactInquiry.class);
        verify(repository).save(captor.capture());
        WebsiteContactInquiry saved = captor.getValue();
        assertEquals("Ada Lovelace", saved.getFullName());
        assertEquals("ada@example.com", saved.getWorkEmail());
        assertEquals("Analytical Engines", saved.getOrganization());
        assertEquals("DEMO", saved.getInquiryType());
        assertNotNull(saved.getClientIpHash());
    }
}
