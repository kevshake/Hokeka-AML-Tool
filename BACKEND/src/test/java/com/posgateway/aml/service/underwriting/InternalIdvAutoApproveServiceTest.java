package com.posgateway.aml.service.underwriting;

import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.BeneficialOwnerRepository;
import com.posgateway.aml.repository.MerchantRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalIdvAutoApproveServiceTest {
    @Test
    void approvesOnlyCompleteHashEvidenceWithoutAdverseLinks() {
        MerchantRepository merchants = mock(MerchantRepository.class);
        BeneficialOwnerRepository owners = mock(BeneficialOwnerRepository.class);
        Merchant merchant = merchant(1L, 10L, "settlement-hash");
        BeneficialOwner owner = new BeneficialOwner();
        owner.setMerchant(merchant);
        owner.setNationalIdHash("ubo-hash");
        merchant.setBeneficialOwners(List.of(owner));
        when(merchants.findByCbkSettlementAccountHash("settlement-hash")).thenReturn(List.of(merchant));
        when(owners.findByNationalIdHash("ubo-hash")).thenReturn(List.of(owner));

        var result = new InternalIdvAutoApproveService(merchants, owners)
                .evaluate(merchant, List.of());

        assertTrue(result.approved());
    }

    @Test
    void rejectsAdverseCrossPspSettlementHashWithoutReturningPii() {
        MerchantRepository merchants = mock(MerchantRepository.class);
        BeneficialOwnerRepository owners = mock(BeneficialOwnerRepository.class);
        Merchant merchant = merchant(1L, 10L, "settlement-hash");
        BeneficialOwner owner = new BeneficialOwner();
        owner.setNationalIdHash("ubo-hash");
        merchant.setBeneficialOwners(List.of(owner));
        Merchant blocked = merchant(2L, 20L, "settlement-hash");
        blocked.setStatus("BLOCKED");
        when(merchants.findByCbkSettlementAccountHash("settlement-hash"))
                .thenReturn(List.of(merchant, blocked));
        when(owners.findByNationalIdHash("ubo-hash")).thenReturn(List.of(owner));

        var result = new InternalIdvAutoApproveService(merchants, owners)
                .evaluate(merchant, List.of());

        assertFalse(result.approved());
        assertEquals("ADVERSE_CROSS_PSP_HASH_LINK", result.reasonCode());
    }

    private static Merchant merchant(Long id, Long pspId, String hash) {
        Psp psp = new Psp();
        psp.setPspId(pspId);
        Merchant merchant = new Merchant();
        merchant.setMerchantId(id);
        merchant.setPsp(psp);
        merchant.setCbkSettlementAccountHash(hash);
        return merchant;
    }
}
