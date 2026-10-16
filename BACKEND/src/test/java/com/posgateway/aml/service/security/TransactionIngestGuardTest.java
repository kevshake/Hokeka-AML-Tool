package com.posgateway.aml.service.security;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.MerchantRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Ingest must only accept merchants of the caller's PSP and reject malformed ids as 400/404. */
class TransactionIngestGuardTest {

    private final MerchantRepository merchants = mock(MerchantRepository.class);
    private final PspIsolationService isolation = mock(PspIsolationService.class);
    private final TransactionIngestGuard guard = new TransactionIngestGuard(merchants, isolation);

    private static Psp psp(long id) {
        Psp p = new Psp();
        p.setPspId(id);
        return p;
    }

    private static User caller(long pspId, String role) {
        Role r = new Role();
        r.setName(role);
        return User.builder().username("u").email("u@x").passwordHash("").role(r).psp(psp(pspId)).build();
    }

    private Merchant merchantOf(long pspId) {
        Merchant m = new Merchant();
        m.setPsp(psp(pspId));
        return m;
    }

    @Test
    void ownMerchantIsAccepted() {
        when(isolation.getCurrentUser()).thenReturn(caller(1L, "PSP_INTEGRATION"));
        when(merchants.findById(10L)).thenReturn(Optional.of(merchantOf(1L)));
        assertThat(guard.requireIngestableMerchant("10")).isNotNull();
    }

    @Test
    void foreignMerchantLooksExactlyLikeMissingOne() {
        when(isolation.getCurrentUser()).thenReturn(caller(1L, "PSP_USER"));
        when(merchants.findById(10L)).thenReturn(Optional.of(merchantOf(2L)));
        when(merchants.findById(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireIngestableMerchant("10"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Merchant not found: 10");
        assertThatThrownBy(() -> guard.requireIngestableMerchant("11"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Merchant not found: 11");
    }

    @Test
    void platformAdminMayIngestForAnyPsp() {
        User admin = caller(0L, "SUPER_ADMIN");
        when(isolation.getCurrentUser()).thenReturn(admin);
        when(isolation.isPlatformAdministrator(any(User.class))).thenReturn(true);
        when(merchants.findById(10L)).thenReturn(Optional.of(merchantOf(2L)));
        assertThat(guard.requireIngestableMerchant("10")).isNotNull();
    }

    @Test
    void malformedIdsAreBadRequestsAndAnonymousIsForbidden() {
        assertThatThrownBy(() -> guard.requireIngestableMerchant("abc"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("numeric");
        assertThatThrownBy(() -> guard.requireIngestableMerchant(" "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("required");
        assertThatThrownBy(() -> guard.requireIngestableMerchant("10")).isInstanceOf(SecurityException.class);

        assertThatThrownBy(() -> guard.requireValidEdgeAssessmentId("edge-123"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("edgeAssessmentId must be a UUID");
        assertThatCode(() -> guard.requireValidEdgeAssessmentId(java.util.UUID.randomUUID().toString()))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.requireValidEdgeAssessmentId(null)).doesNotThrowAnyException();
    }
}
