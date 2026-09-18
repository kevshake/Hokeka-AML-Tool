package com.posgateway.aml.service.document;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.merchant.MerchantDocument;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.MerchantDocumentRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.document.DocumentAccessLogRepository;
import com.posgateway.aml.service.cache.DocumentAccessCacheService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentAccessControlServiceTest {

    @Mock private MerchantDocumentRepository documentRepository;
    @Mock private DocumentAccessLogRepository accessLogRepository;
    @Mock private DocumentAccessCacheService accessCacheService;
    @Mock private UserRepository userRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private PspIsolationService pspIsolationService;

    private DocumentAccessControlService service;

    @BeforeEach
    void setUp() {
        service = new DocumentAccessControlService(
                documentRepository,
                accessLogRepository,
                accessCacheService,
                userRepository,
                merchantRepository,
                pspIsolationService);
    }

    @Test
    void samePspUserCanAccessAndCallerRoleCannotEscalatePrivileges() {
        Psp psp = psp(4L);
        User user = user(11L, "PSP_USER", psp);
        Merchant merchant = merchant(21L, psp);
        MerchantDocument document = document(31L, 21L);

        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        when(accessCacheService.getCachedAccessPermission(31L, 11L, "PSP_USER"))
                .thenReturn(null);
        when(documentRepository.findById(31L)).thenReturn(Optional.of(document));
        when(merchantRepository.findById(21L)).thenReturn(Optional.of(merchant));
        when(pspIsolationService.isPlatformAdministrator(user)).thenReturn(false);

        assertTrue(service.canAccessDocument(31L, 11L, "SUPER_ADMIN"));
        verify(accessCacheService).cacheAccessPermission(31L, 11L, "PSP_USER", true);
    }

    @Test
    void crossPspUserIsDenied() {
        User user = user(12L, "PSP_ADMIN", psp(4L));
        Merchant merchant = merchant(22L, psp(5L));
        MerchantDocument document = document(32L, 22L);

        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(accessCacheService.getCachedAccessPermission(32L, 12L, "PSP_ADMIN"))
                .thenReturn(null);
        when(documentRepository.findById(32L)).thenReturn(Optional.of(document));
        when(merchantRepository.findById(22L)).thenReturn(Optional.of(merchant));
        when(pspIsolationService.isPlatformAdministrator(user)).thenReturn(false);

        assertFalse(service.canAccessDocument(32L, 12L, "PSP_ADMIN"));
        verify(accessCacheService).cacheAccessPermission(32L, 12L, "PSP_ADMIN", false);
    }

    private static User user(Long id, String roleName, Psp psp) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setPsp(psp);
        return user;
    }

    private static Psp psp(Long id) {
        Psp psp = new Psp();
        psp.setPspId(id);
        return psp;
    }

    private static Merchant merchant(Long id, Psp psp) {
        Merchant merchant = new Merchant();
        merchant.setMerchantId(id);
        merchant.setPsp(psp);
        return merchant;
    }

    private static MerchantDocument document(Long id, Long merchantId) {
        MerchantDocument document = new MerchantDocument();
        document.setDocumentId(id);
        document.setMerchantId(merchantId);
        return document;
    }
}
