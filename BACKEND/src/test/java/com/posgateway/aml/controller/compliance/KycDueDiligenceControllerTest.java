package com.posgateway.aml.controller.compliance;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.compliance.AuditService;
import com.posgateway.aml.service.corporate.CorporateIntelligenceService;
import com.posgateway.aml.service.edd.EnhancedDueDiligenceService;
import com.posgateway.aml.service.kyc.BeneficialOwnershipService;
import com.posgateway.aml.service.kyc.KycCompletenessService;
import com.posgateway.aml.service.kyc.RiskBasedCddService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KycDueDiligenceControllerTest {
    @Mock MerchantRepository merchantRepository;
    @Mock PspIsolationService isolationService;
    @Mock BeneficialOwnershipService ownershipService;
    @Mock EnhancedDueDiligenceService eddService;
    @Mock RiskBasedCddService cddService;
    @Mock KycCompletenessService completenessService;
    @Mock AuditService auditService;
    @Mock CorporateIntelligenceService corporateIntelligenceService;
    private KycDueDiligenceController controller;

    @BeforeEach
    void setUp() {
        controller = new KycDueDiligenceController(merchantRepository, isolationService, ownershipService,
                eddService, cddService, completenessService, auditService, corporateIntelligenceService);
    }

    @Test
    void tenantCannotReadAnotherPspDueDiligence() {
        User user = userForPsp(1L);
        Merchant merchant = new Merchant();
        merchant.setPsp(psp(2L));
        when(merchantRepository.findById(9L)).thenReturn(Optional.of(merchant));
        when(isolationService.getCurrentUser()).thenReturn(user);
        when(isolationService.isPlatformAdministrator(user)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> controller.overview(9L));

        verifyNoInteractions(cddService, completenessService, ownershipService, eddService);
    }

    private User userForPsp(Long id) { return User.builder().username("tenant").psp(psp(id)).build(); }
    private Psp psp(Long id) { Psp psp = new Psp(); psp.setPspId(id); return psp; }
}
