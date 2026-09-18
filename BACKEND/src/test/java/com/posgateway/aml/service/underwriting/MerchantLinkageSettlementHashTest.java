package com.posgateway.aml.service.underwriting;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.BeneficialOwnerRepository;
import com.posgateway.aml.repository.MerchantRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class MerchantLinkageSettlementHashTest {
    @Test
    void linksSettlementAccountsByHashOnly() {
        MerchantRepository merchants = mock(MerchantRepository.class);
        BeneficialOwnerRepository owners = mock(BeneficialOwnerRepository.class);
        Merchant current = new Merchant();
        current.setMerchantId(1L);
        current.setCbkSettlementAccountNumber("plaintext-one");
        current.setCbkSettlementAccountHash("hash");
        Merchant blocked = new Merchant();
        blocked.setMerchantId(2L);
        blocked.setStatus("BLOCKED");
        when(merchants.findByCbkSettlementAccountHash("hash")).thenReturn(List.of(blocked));

        var signals = new MerchantLinkageService(merchants, owners).findLinkages(current);

        assertTrue(signals.stream().anyMatch(s -> "MERCHANT_REINCARNATION".equals(s.getSignalCode())));
        verify(merchants).findByCbkSettlementAccountHash("hash");
        verify(merchants, never()).findByCbkSettlementAccountHash("plaintext-one");
    }
}
