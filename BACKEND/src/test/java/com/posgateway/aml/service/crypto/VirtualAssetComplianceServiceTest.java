package com.posgateway.aml.service.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.client.blockchain.BlockchainAnalyticsClient;
import com.posgateway.aml.client.blockchain.TravelRuleGatewayClient;
import com.posgateway.aml.dto.crypto.VirtualAssetDtos.PrepareTravelRuleRequest;
import com.posgateway.aml.dto.crypto.VirtualAssetDtos.VerifyIdentityRequest;
import com.posgateway.aml.dto.crypto.VirtualAssetDtos.SaveVaspRequest;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.entity.crypto.*;
import com.posgateway.aml.entity.multiasset.*;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.crypto.*;
import com.posgateway.aml.repository.multiasset.*;
import com.posgateway.aml.service.security.PspIsolationService;
import com.posgateway.aml.service.multiasset.MultiAssetRiskEngine.CryptoScreeningAssessment;
import com.posgateway.aml.service.aml.AerospikeSanctionsScreeningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VirtualAssetComplianceServiceTest {
    @Mock VaspDirectoryRepository vaspRepository;
    @Mock VaspScreeningRecordRepository vaspScreeningRepository;
    @Mock CryptoWalletProfileRepository walletRepository;
    @Mock WalletScreeningRecordRepository screeningRepository;
    @Mock TravelRulePolicyRepository policyRepository;
    @Mock TravelRuleTransferRepository transferRepository;
    @Mock TravelRuleTransmissionAttemptRepository attemptRepository;
    @Mock VirtualAssetRegulatorAccessGrantRepository grantRepository;
    @Mock VirtualAssetRegulatorAccessLogRepository accessLogRepository;
    @Mock AssetAccountRepository accountRepository;
    @Mock MultiAssetTransactionRepository transactionRepository;
    @Mock MultiAssetRiskSignalRepository signalRepository;
    @Mock AlertRepository alertRepository;
    @Mock BlockchainAnalyticsClient blockchainClient;
    @Mock TravelRuleGatewayClient travelRuleClient;
    @Mock PspIsolationService pspIsolationService;
    @Mock AerospikeSanctionsScreeningService sanctionsScreeningService;

    private VirtualAssetComplianceService service;
    private MultiAssetTransaction transaction;

    @BeforeEach
    void setUp() {
        service = new VirtualAssetComplianceService(vaspRepository, vaspScreeningRepository, walletRepository, screeningRepository,
                policyRepository, transferRepository, attemptRepository, grantRepository, accessLogRepository,
                accountRepository, transactionRepository, signalRepository, alertRepository, blockchainClient,
                travelRuleClient, pspIsolationService, new ObjectMapper(), sanctionsScreeningService, 24);
        MultiAssetCustomer customer = new MultiAssetCustomer(); customer.setId(9L); customer.setPspId(1L);
        customer.setExternalCustomerId("CUST-9"); customer.setDisplayName("Customer Nine");
        transaction = new MultiAssetTransaction(); transaction.setId(100L); transaction.setPspId(1L);
        transaction.setCustomer(customer); transaction.setExternalTransactionId("CRYPTO-TX-100");
        transaction.setAssetClass(AssetClass.CRYPTO); transaction.setProductDomain(ProductDomain.VIRTUAL_ASSET);
        transaction.setTransactionType(MultiAssetTransactionType.TRANSFER); transaction.setAmount(BigDecimal.valueOf(2_000));
        transaction.setFiatEquivalentUsd(BigDecimal.valueOf(2_000)); transaction.setCurrency("USDC");
        transaction.setExecutedAt(LocalDateTime.of(2026, 7, 15, 12, 0));
    }

    @Test
    void appliesJurisdictionPolicyAndRequiresBothIdentityVerifications() {
        stubTransferPreparation();
        TravelRuleJurisdictionPolicy policy = policy();
        when(policyRepository.findActive(eq(1L), eq("KE"), any())).thenReturn(List.of(policy));

        var result = service.prepareTransfer(new PrepareTravelRuleRequest(100L, "KE", null, null,
                "TRISA", Map.of("originatorName", "Alice", "originatorAccount", "A-1",
                        "beneficiaryName", "Bob", "beneficiaryAccount", "B-1")));

        assertThat(result.status()).isEqualTo(TravelRuleTransferStatus.PENDING_VERIFICATION);
        assertThat(result.payloadHash()).hasSize(64);
        assertThat(result.retainUntil()).isEqualTo(transaction.getExecutedAt().toLocalDate().plusYears(7));
        assertThat(transaction.isTravelRuleRequired()).isTrue();
        assertThat(transaction.getTravelRuleStatus()).isEqualTo(TravelRuleStatus.PENDING_VERIFICATION);
    }

    @Test
    void failsClosedWhenNoJurisdictionPolicyExists() {
        stubTransferPreparation();
        when(policyRepository.findActive(eq(1L), eq("UG"), any())).thenReturn(List.of());

        var result = service.prepareTransfer(new PrepareTravelRuleRequest(100L, "UG", null, null,
                "TRISA", Map.of("originatorName", "Alice")));

        assertThat(result.status()).isEqualTo(TravelRuleTransferStatus.PENDING_DATA);
        assertThat(result.failureReason()).isEqualTo("No active jurisdiction policy");
        assertThat(transaction.getTravelRuleStatus()).isEqualTo(TravelRuleStatus.INCOMPLETE);
    }

    @Test
    void retainsVerificationEvidenceAndAllowsDocumentedRemediation() {
        stubAuthenticatedTransferSave();
        TravelRuleTransfer transfer = new TravelRuleTransfer();
        transfer.setId(30L); transfer.setPspId(1L); transfer.setTransaction(transaction);
        transfer.setStatus(TravelRuleTransferStatus.REJECTED);
        transfer.setFailureReason("Identity verification failed");
        transfer.setOriginatorVerification(IdentityVerificationStatus.FAILED);
        transfer.setBeneficiaryVerification(IdentityVerificationStatus.VERIFIED);
        when(transferRepository.findByIdAndPspId(30L, 1L)).thenReturn(Optional.of(transfer));

        var result = service.verifyIdentity(30L,
                new VerifyIdentityRequest("ORIGINATOR", true, "KYC-DOC-2026-441"));

        assertThat(result.status()).isEqualTo(TravelRuleTransferStatus.READY);
        assertThat(result.originatorVerificationReference()).isEqualTo("KYC-DOC-2026-441");
        assertThat(result.originatorVerifiedBy()).isEqualTo("SYSTEM");
        assertThat(result.originatorVerifiedAt()).isNotNull();
        assertThat(result.failureReason()).isNull();
        assertThat(transaction.getTravelRuleStatus()).isEqualTo(TravelRuleStatus.PENDING_VERIFICATION);
    }

    @Test
    void persistsTransactionCounterpartyScreeningWithoutMisattributingARegisteredWallet() {
        var providerResult = BlockchainAnalyticsClient.WalletRiskResult.unavailable(
                "TEST_PROVIDER", "Provider request failed");
        when(walletRepository.findByPspIdAndNetworkAndWalletAddress(1L, "bitcoin", "bc1-counterparty"))
                .thenReturn(Optional.empty());
        when(screeningRepository.save(any())).thenAnswer(invocation -> {
            WalletScreeningRecord record = invocation.getArgument(0); record.setId(77L); return record;
        });

        var response = service.recordTransactionScreening(transaction,
                new CryptoScreeningAssessment("bc1-counterparty", "bitcoin", providerResult),
                WalletScreeningTrigger.PRE_WITHDRAWAL);

        assertThat(response.id()).isEqualTo(77L);
        assertThat(response.walletProfileId()).isNull();
        assertThat(response.customerId()).isEqualTo(9L);
        assertThat(response.transactionId()).isEqualTo(100L);
        assertThat(response.screenedAddress()).isEqualTo("bc1-counterparty");
        assertThat(response.network()).isEqualTo("bitcoin");
        assertThat(response.available()).isFalse();
    }

    @Test
    void sanctionsMatchOverridesOperatorPermissionAndPersistsEvidence() {
        when(pspIsolationService.getCurrentUserPspId()).thenReturn(1L);
        when(vaspRepository.save(any())).thenAnswer(invocation -> {
            VaspDirectoryEntry vasp = invocation.getArgument(0); vasp.setId(41L); return vasp;
        });
        when(vaspScreeningRepository.save(any())).thenAnswer(invocation -> {
            VaspScreeningRecord record = invocation.getArgument(0); record.setId(91L); return record;
        });
        when(sanctionsScreeningService.screenName("Risky Exchange", ScreeningResult.EntityType.ORGANIZATION))
                .thenReturn(ScreeningResult.builder().screenedName("Risky Exchange")
                        .entityType(ScreeningResult.EntityType.ORGANIZATION)
                        .status(ScreeningResult.ScreeningStatus.MATCH).matchCount(1)
                        .screeningProvider("AEROSPIKE").build());
        when(alertRepository.existsByPspIdAndSourceTypeAndSourceReference(
                1L, "VASP_SANCTIONS_SCREENING", "41")).thenReturn(false);

        var response = service.saveVasp(null, new SaveVaspRequest(
                "Risky Exchange", null, "KE", "CBK", "LIC-41", VaspLicenseStatus.LICENSED,
                null, "REG-41", null, List.of("TRISA"), "CLEAR", List.of(), List.of(),
                10, VaspTransferDecision.PERMITTED, null, LocalDateTime.now().plusDays(30)));

        assertThat(response.sanctionsStatus()).isEqualTo("MATCH");
        assertThat(response.sanctionsMatchCount()).isEqualTo(1);
        assertThat(response.riskScore()).isEqualTo(100);
        assertThat(response.transferDecision()).isEqualTo(VaspTransferDecision.PROHIBITED);
        verify(vaspScreeningRepository).save(any(VaspScreeningRecord.class));
        verify(alertRepository).save(any(Alert.class));
    }

    private TravelRuleJurisdictionPolicy policy() {
        TravelRuleJurisdictionPolicy policy = new TravelRuleJurisdictionPolicy(); policy.setId(5L);
        policy.setPspId(1L); policy.setJurisdiction("KE"); policy.setPolicyCode("KE_TEST");
        policy.setThresholdUsd(BigDecimal.ZERO); policy.setAppliesToAllTransfers(true);
        policy.setRequiredFields(List.of("originatorName", "originatorAccount", "beneficiaryName", "beneficiaryAccount"));
        policy.setVerifyOriginator(true); policy.setVerifyBeneficiary(true);
        policy.setAcceptedProtocols(List.of("TRISA")); policy.setRetentionYears(7);
        return policy;
    }

    private void stubTransferPreparation() {
        stubAuthenticatedTransferSave();
        when(transactionRepository.findByIdAndPspId(100L, 1L)).thenReturn(Optional.of(transaction));
        when(transferRepository.findByPspIdAndTransactionId(1L, 100L)).thenReturn(Optional.empty());
    }

    private void stubAuthenticatedTransferSave() {
        when(pspIsolationService.getCurrentUserPspId()).thenReturn(1L);
        when(transferRepository.save(any())).thenAnswer(invocation -> {
            TravelRuleTransfer transfer = invocation.getArgument(0); transfer.setId(30L); return transfer;
        });
    }
}
