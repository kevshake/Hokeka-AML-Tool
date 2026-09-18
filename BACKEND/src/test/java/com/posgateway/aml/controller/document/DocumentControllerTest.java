package com.posgateway.aml.controller.document;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.merchant.MerchantDocument;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.MerchantDocumentRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.compliance.AuditService;
import com.posgateway.aml.service.document.DocumentManagementService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock DocumentManagementService documentService;
    @Mock MerchantDocumentRepository documentRepository;
    @Mock MerchantRepository merchantRepository;
    @Mock AuditService auditService;
    @Mock PspIsolationService isolationService;

    private DocumentController controller;

    @BeforeEach
    void setUp() {
        controller = new DocumentController(documentService, documentRepository, merchantRepository,
                auditService, isolationService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tenantCannotListAnotherPspDocuments() {
        User user = authenticateTenant(1L);
        Merchant merchant = merchantForPsp(2L);
        when(merchantRepository.findById(20L)).thenReturn(Optional.of(merchant));
        when(isolationService.isPlatformAdministrator(user)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> controller.getDocuments(20L));

        verifyNoInteractions(documentService);
    }

    @Test
    void tenantCannotReviewAnotherPspDocument() {
        User user = authenticateTenant(3L);
        MerchantDocument document = new MerchantDocument();
        document.setMerchantId(40L);
        when(documentRepository.findById(8L)).thenReturn(Optional.of(document));
        when(merchantRepository.findById(40L)).thenReturn(Optional.of(merchantForPsp(4L)));
        when(isolationService.isPlatformAdministrator(user)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> controller.verifyDocument(8L, true, "reviewed"));

        verify(documentService, never()).verifyDocument(anyLong(), anyBoolean(), anyString(), any());
    }

    private User authenticateTenant(Long pspId) {
        Psp psp = new Psp();
        psp.setPspId(pspId);
        Role role = new Role();
        role.setName("PSP_ADMIN");
        User user = User.builder().username("tenant-" + pspId).psp(psp).role(role).enabled(true).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        return user;
    }

    private Merchant merchantForPsp(Long pspId) {
        Psp psp = new Psp();
        psp.setPspId(pspId);
        Merchant merchant = new Merchant();
        merchant.setPsp(psp);
        return merchant;
    }
}
