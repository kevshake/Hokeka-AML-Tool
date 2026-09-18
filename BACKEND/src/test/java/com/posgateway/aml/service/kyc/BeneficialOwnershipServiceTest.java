package com.posgateway.aml.service.kyc;

import com.posgateway.aml.dto.request.BeneficialOwnerRequest;
import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.BeneficialOwnerRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.aml.AmlScreeningOrchestrator;
import com.posgateway.aml.service.security.PiiLookupHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BeneficialOwnershipServiceTest {
    @Mock BeneficialOwnerRepository ownerRepository;
    @Mock MerchantRepository merchantRepository;
    @Mock AmlScreeningOrchestrator orchestrator;

    @Test
    void rejectsOwnershipTotalsAboveOneHundred() {
        Merchant merchant = new Merchant();
        BeneficialOwner existing = new BeneficialOwner();
        existing.setOwnershipPercentage(80);
        when(merchantRepository.findById(4L)).thenReturn(Optional.of(merchant));
        when(ownerRepository.findByMerchant_MerchantId(4L)).thenReturn(List.of(existing));
        BeneficialOwnershipService service = service();

        assertThrows(IllegalArgumentException.class, () -> service.create(4L, request(30)));

        verify(ownerRepository, never()).save(any());
    }

    @Test
    void createStoresKeyedIdentifierHashes() {
        Merchant merchant = new Merchant();
        when(merchantRepository.findById(4L)).thenReturn(Optional.of(merchant));
        when(ownerRepository.findByMerchant_MerchantId(4L)).thenReturn(List.of());
        when(ownerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        BeneficialOwnershipService service = service();

        BeneficialOwner owner = service.create(4L, request(25));

        assertNotNull(owner.getPassportHash());
        assertEquals(64, owner.getPassportHash().length());
        assertEquals("A1234567", owner.getPassportNumber());
    }

    private BeneficialOwnershipService service() {
        return new BeneficialOwnershipService(ownerRepository, merchantRepository, orchestrator,
                new PiiLookupHasher("01234567890123456789012345678901"));
    }

    private BeneficialOwnerRequest request(int percentage) {
        return new BeneficialOwnerRequest("Jane Owner", LocalDate.of(1985, 1, 1), "KEN", "KEN",
                "A1234567", null, percentage);
    }
}
