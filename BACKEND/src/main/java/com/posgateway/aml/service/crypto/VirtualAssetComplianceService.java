package com.posgateway.aml.service.crypto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.client.blockchain.BlockchainAnalyticsClient;
import com.posgateway.aml.client.blockchain.TravelRuleGatewayClient;
import com.posgateway.aml.dto.crypto.VirtualAssetDtos.*;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.crypto.*;
import com.posgateway.aml.entity.multiasset.*;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.crypto.*;
import com.posgateway.aml.repository.multiasset.*;
import com.posgateway.aml.service.security.PspIsolationService;
import com.posgateway.aml.service.multiasset.MultiAssetRiskEngine.CryptoScreeningAssessment;
import com.posgateway.aml.service.aml.AerospikeSanctionsScreeningService;
import com.posgateway.aml.model.ScreeningResult;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class VirtualAssetComplianceService {
    private static final Logger log = LoggerFactory.getLogger(VirtualAssetComplianceService.class);
    private static final Set<String> GRANT_SCOPES = Set.of("TRANSACTIONS_READ", "WALLET_SCREENING_READ", "TRAVEL_RULE_READ", "TRAVEL_RULE_PII");

    private final VaspDirectoryRepository vaspRepository;
    private final VaspScreeningRecordRepository vaspScreeningRepository;
    private final CryptoWalletProfileRepository walletRepository;
    private final WalletScreeningRecordRepository screeningRepository;
    private final TravelRulePolicyRepository policyRepository;
    private final TravelRuleTransferRepository transferRepository;
    private final TravelRuleTransmissionAttemptRepository attemptRepository;
    private final VirtualAssetRegulatorAccessGrantRepository grantRepository;
    private final VirtualAssetRegulatorAccessLogRepository accessLogRepository;
    private final AssetAccountRepository accountRepository;
    private final MultiAssetTransactionRepository transactionRepository;
    private final MultiAssetRiskSignalRepository signalRepository;
    private final AlertRepository alertRepository;
    private final BlockchainAnalyticsClient blockchainClient;
    private final TravelRuleGatewayClient travelRuleClient;
    private final PspIsolationService pspIsolationService;
    private final ObjectMapper objectMapper;
    private final AerospikeSanctionsScreeningService sanctionsScreeningService;
    private final int vaspScreeningIntervalHours;

    public VirtualAssetComplianceService(VaspDirectoryRepository vaspRepository,
            VaspScreeningRecordRepository vaspScreeningRepository,
            CryptoWalletProfileRepository walletRepository,
            WalletScreeningRecordRepository screeningRepository,
            TravelRulePolicyRepository policyRepository,
            TravelRuleTransferRepository transferRepository,
            TravelRuleTransmissionAttemptRepository attemptRepository,
            VirtualAssetRegulatorAccessGrantRepository grantRepository,
            VirtualAssetRegulatorAccessLogRepository accessLogRepository,
            AssetAccountRepository accountRepository,
            MultiAssetTransactionRepository transactionRepository,
            MultiAssetRiskSignalRepository signalRepository,
            AlertRepository alertRepository,
            BlockchainAnalyticsClient blockchainClient,
            TravelRuleGatewayClient travelRuleClient,
            PspIsolationService pspIsolationService,
            ObjectMapper objectMapper,
            AerospikeSanctionsScreeningService sanctionsScreeningService,
            @Value("${crypto.vasp-screening.interval-hours:24}") int vaspScreeningIntervalHours) {
        this.vaspRepository = vaspRepository;
        this.vaspScreeningRepository = vaspScreeningRepository;
        this.walletRepository = walletRepository;
        this.screeningRepository = screeningRepository;
        this.policyRepository = policyRepository;
        this.transferRepository = transferRepository;
        this.attemptRepository = attemptRepository;
        this.grantRepository = grantRepository;
        this.accessLogRepository = accessLogRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.signalRepository = signalRepository;
        this.alertRepository = alertRepository;
        this.blockchainClient = blockchainClient;
        this.travelRuleClient = travelRuleClient;
        this.pspIsolationService = pspIsolationService;
        this.objectMapper = objectMapper;
        this.sanctionsScreeningService = sanctionsScreeningService;
        this.vaspScreeningIntervalHours = Math.max(1, vaspScreeningIntervalHours);
    }

    @Transactional
    public VaspResponse saveVasp(Long id, SaveVaspRequest request) {
        Long pspId = requiredPspId();
        VaspDirectoryEntry vasp = id == null ? new VaspDirectoryEntry()
                : vaspRepository.findByIdAndPspId(id, pspId)
                    .orElseThrow(() -> new IllegalArgumentException("VASP not found: " + id));
        vasp.setPspId(pspId);
        vasp.setLegalName(required(request.legalName(), "legalName"));
        vasp.setTradingName(optional(request.tradingName()));
        vasp.setJurisdiction(country(request.jurisdiction()));
        vasp.setRegulatorName(optional(request.regulatorName()));
        vasp.setLicenceNumber(optional(request.licenceNumber()));
        vasp.setLicenceStatus(request.licenceStatus());
        vasp.setLicenceValidUntil(request.licenceValidUntil());
        vasp.setRegistrationNumber(optional(request.registrationNumber()));
        vasp.setWebsite(optional(request.website()));
        vasp.setTravelRuleProtocols(normalizedList(request.travelRuleProtocols()));
        vasp.setBeneficialOwnership(copyMaps(request.beneficialOwnership()));
        vasp.setWalletClusters(copyMaps(request.walletClusters()));
        vasp.setRiskScore(request.riskScore());
        vasp.setRiskLevel(riskLevel(request.riskScore()));
        vasp.setTransferDecision(request.transferDecision());
        vasp.setReviewNotes(optional(request.reviewNotes()));
        vasp.setLastReviewedAt(LocalDateTime.now());
        vasp.setNextReviewAt(request.nextReviewAt());
        vasp = vaspRepository.save(vasp);
        screenVaspInternal(vasp);
        return toVasp(vasp);
    }

    @Transactional(readOnly = true)
    public Page<VaspResponse> listVasps(String search, int page, int size) {
        Long pspId = requiredPspId();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), bounded(size));
        Page<VaspDirectoryEntry> result = search == null || search.isBlank()
                ? vaspRepository.findByPspIdOrderByRiskScoreDescLegalNameAsc(pspId, pageable)
                : vaspRepository.findByPspIdAndLegalNameContainingIgnoreCaseOrderByRiskScoreDesc(pspId, search.trim(), pageable);
        return result.map(this::toVasp);
    }

    @Transactional
    public VaspResponse screenVasp(Long id) {
        VaspDirectoryEntry vasp = vasp(id, requiredPspId());
        screenVaspInternal(vasp);
        return toVasp(vasp);
    }

    @Transactional(readOnly = true)
    public Page<VaspScreeningResponse> vaspScreeningHistory(Long vaspId, int page, int size) {
        Long pspId = requiredPspId();
        if (vaspId != null) vasp(vaspId, pspId);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), bounded(size));
        return (vaspId == null ? vaspScreeningRepository.findByPspIdOrderByScreenedAtDesc(pspId, pageable)
                : vaspScreeningRepository.findByVaspIdAndPspIdOrderByScreenedAtDesc(vaspId, pspId, pageable))
                .map(this::toVaspScreening);
    }

    @Scheduled(cron = "${crypto.vasp-screening.cron:0 20 * * * *}")
    @Transactional
    public void screenDueVasps() {
        for (VaspDirectoryEntry vasp : vaspRepository
                .findTop100BySanctionsNextScreeningAtLessThanEqualOrderBySanctionsNextScreeningAtAsc(LocalDateTime.now())) {
            try { screenVaspInternal(vasp); }
            catch (RuntimeException exception) { log.error("Periodic VASP screening failed for {}", vasp.getId(), exception); }
        }
    }

    @Transactional
    public WalletProfileResponse registerWallet(RegisterWalletRequest request) {
        Long pspId = requiredPspId();
        AssetAccount account = accountRepository.findByIdAndPspId(request.assetAccountId(), pspId)
                .orElseThrow(() -> new IllegalArgumentException("Crypto asset account not found: " + request.assetAccountId()));
        if (account.getAssetClass() != AssetClass.CRYPTO || account.getPublicAddress() == null || account.getPublicAddress().isBlank()) {
            throw new IllegalArgumentException("Asset account must be a crypto account with a public wallet address");
        }
        CryptoWalletProfile profile = walletRepository.findByPspIdAndAssetAccountId(pspId, account.getId())
                .orElseGet(CryptoWalletProfile::new);
        profile.setPspId(pspId);
        profile.setAssetAccount(account);
        profile.setCustomer(account.getCustomer());
        profile.setVasp(request.vaspId() == null ? null : vasp(request.vaspId(), pspId));
        profile.setWalletAddress(account.getPublicAddress().trim());
        profile.setNetwork(required(request.network(), "network"));
        profile.setAddressLabel(optional(request.addressLabel()));
        profile.setOwnershipType(optional(request.ownershipType()) == null ? "CUSTOMER" : request.ownershipType().trim().toUpperCase(Locale.ROOT));
        profile.setScreeningIntervalHours(request.screeningIntervalHours() == null ? 24 : request.screeningIntervalHours());
        profile.setNextScreeningAt(LocalDateTime.now());
        profile = walletRepository.save(profile);
        screenInternal(profile, WalletScreeningTrigger.ONBOARDING, null);
        return toWallet(profile);
    }

    @Transactional(readOnly = true)
    public Page<WalletProfileResponse> listWallets(int page, int size) {
        return walletRepository.findByPspIdOrderByLatestRiskScoreDescUpdatedAtDesc(requiredPspId(),
                PageRequest.of(Math.max(page, 0), bounded(size))).map(this::toWallet);
    }

    @Transactional(readOnly = true)
    public Page<CryptoTransactionSummary> listCryptoTransactions(int page, int size) {
        return transactionRepository.findByPspIdAndAssetClassOrderByExecutedAtDesc(requiredPspId(), AssetClass.CRYPTO,
                PageRequest.of(Math.max(page, 0), bounded(size))).map(transaction -> new CryptoTransactionSummary(
                        transaction.getId(), transaction.getExternalTransactionId(), transaction.getCustomer().getId(),
                        transaction.getCustomer().getDisplayName(), transaction.getTransactionType().name(),
                        transaction.getAmount(), transaction.getFiatEquivalentUsd(), transaction.getCurrency(),
                        transaction.getAssetSymbol(), transaction.getCounterpartyReference(), transaction.getSourceNetwork(),
                        transaction.getDestinationNetwork(), transaction.getRiskScore(), transaction.getDecision().name(),
                        transaction.getTravelRuleStatus().name(), transaction.getExecutedAt()));
    }

    @Transactional
    public WalletScreeningResponse screenWallet(Long walletId, WalletScreeningTrigger trigger, Long transactionId) {
        Long pspId = requiredPspId();
        CryptoWalletProfile wallet = walletRepository.findByIdAndPspId(walletId, pspId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet profile not found: " + walletId));
        MultiAssetTransaction transaction = transactionId == null ? null
                : transactionRepository.findByIdAndPspId(transactionId, pspId)
                    .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
        return toScreening(screenInternal(wallet, trigger, transaction));
    }

    @Transactional
    public WalletScreeningResponse recordTransactionScreening(MultiAssetTransaction transaction,
            CryptoScreeningAssessment assessment, WalletScreeningTrigger trigger) {
        if (assessment == null || assessment.result() == null) return null;
        CryptoWalletProfile profile = assessment.address() == null || assessment.network() == null ? null
                : walletRepository.findByPspIdAndNetworkAndWalletAddress(
                        transaction.getPspId(), assessment.network(), assessment.address()).orElse(null);
        WalletScreeningRecord record = persistScreening(profile, transaction.getCustomer(), transaction,
                trigger, assessment.address(), assessment.network(), assessment.result());
        if (profile != null) updateWalletProfile(profile, record, assessment.result());
        return toScreening(record);
    }

    @Transactional(readOnly = true)
    public Page<WalletScreeningResponse> screeningHistory(Long walletId, int page, int size) {
        Long pspId = requiredPspId();
        if (walletId != null) walletRepository.findByIdAndPspId(walletId, pspId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet profile not found: " + walletId));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), bounded(size));
        return (walletId == null ? screeningRepository.findByPspIdOrderByScreenedAtDesc(pspId, pageable)
                : screeningRepository.findByWalletProfileIdAndPspIdOrderByScreenedAtDesc(walletId, pspId, pageable))
                .map(this::toScreening);
    }

    @Scheduled(cron = "${crypto.wallet-screening.cron:0 */15 * * * *}")
    @Transactional
    public void screenDueWallets() {
        for (CryptoWalletProfile wallet : walletRepository
                .findTop100ByStatusAndNextScreeningAtLessThanEqualOrderByNextScreeningAtAsc("ACTIVE", LocalDateTime.now())) {
            try { screenInternal(wallet, WalletScreeningTrigger.PERIODIC, null); }
            catch (RuntimeException exception) { log.error("Periodic wallet screening failed for profile {}", wallet.getId(), exception); }
        }
    }

    @Transactional
    public TravelRulePolicyResponse savePolicy(Long id, SaveTravelRulePolicyRequest request) {
        Long pspId = requiredPspId();
        TravelRuleJurisdictionPolicy policy = id == null ? new TravelRuleJurisdictionPolicy()
                : policyRepository.findByIdAndPspId(id, pspId)
                    .orElseThrow(() -> new IllegalArgumentException("Travel Rule policy not found: " + id));
        policy.setPspId(pspId);
        policy.setJurisdiction(country(request.jurisdiction()));
        policy.setPolicyCode(required(request.policyCode(), "policyCode").toUpperCase(Locale.ROOT));
        policy.setThresholdUsd(request.thresholdUsd());
        policy.setAppliesToAllTransfers(request.appliesToAllTransfers());
        policy.setRequiredFields(normalizedFields(request.requiredFields()));
        policy.setVerifyOriginator(request.verifyOriginator());
        policy.setVerifyBeneficiary(request.verifyBeneficiary());
        policy.setAcceptedProtocols(normalizedList(request.acceptedProtocols()));
        policy.setRetentionYears(request.retentionYears());
        policy.setEffectiveFrom(request.effectiveFrom());
        policy.setEffectiveUntil(request.effectiveUntil());
        policy.setEnabled(request.enabled());
        policy.setLegalReference(optional(request.legalReference()));
        return toPolicy(policyRepository.save(policy));
    }

    @Transactional(readOnly = true)
    public List<TravelRulePolicyResponse> listPolicies() {
        return policyRepository.findByPspIdOrderByJurisdictionAscEffectiveFromDesc(requiredPspId()).stream()
                .map(this::toPolicy).toList();
    }

    @Transactional
    public TravelRuleTransferResponse prepareTransfer(PrepareTravelRuleRequest request) {
        Long pspId = requiredPspId();
        MultiAssetTransaction transaction = transactionRepository.findByIdAndPspId(request.transactionId(), pspId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + request.transactionId()));
        if (transaction.getAssetClass() != AssetClass.CRYPTO) throw new IllegalArgumentException("Travel Rule applies to crypto transfers only");
        TravelRuleTransfer transfer = transferRepository.findByPspIdAndTransactionId(pspId, transaction.getId())
                .orElseGet(TravelRuleTransfer::new);
        transfer.setPspId(pspId);
        transfer.setTransaction(transaction);
        transfer.setJurisdiction(country(request.jurisdiction()));
        transfer.setOriginatorVasp(request.originatorVaspId() == null ? null : vasp(request.originatorVaspId(), pspId));
        transfer.setBeneficiaryVasp(request.beneficiaryVaspId() == null ? null : vasp(request.beneficiaryVaspId(), pspId));
        transfer.setProtocol(optional(request.protocol()));
        TravelRuleJurisdictionPolicy policy = policyRepository.findActive(pspId, transfer.getJurisdiction(), transaction.getExecutedAt())
                .stream().findFirst().orElse(null);
        transfer.setPolicy(policy);
        int retentionYears = policy == null ? 7 : Math.max(7, policy.getRetentionYears());
        transfer.setRetainUntil(transaction.getExecutedAt().toLocalDate().plusYears(retentionYears));
        evaluateTransfer(transfer, request.payload());
        transfer.setEncryptedPayload(json(request.payload()));
        transfer.setPayloadHash(hash(transfer.getEncryptedPayload()));
        updateTransactionTravelRule(transaction, transfer.getStatus());
        return toTransfer(transferRepository.save(transfer));
    }

    @Transactional
    public TravelRuleTransferResponse verifyIdentity(Long transferId, VerifyIdentityRequest request) {
        TravelRuleTransfer transfer = transfer(transferId, requiredPspId());
        IdentityVerificationStatus status = request.verified() ? IdentityVerificationStatus.VERIFIED : IdentityVerificationStatus.FAILED;
        String evidence = required(request.evidenceReference(), "evidenceReference");
        String verifier = currentUsername();
        LocalDateTime verifiedAt = LocalDateTime.now();
        if ("ORIGINATOR".equalsIgnoreCase(request.party())) {
            transfer.setOriginatorVerification(status);
            transfer.setOriginatorVerificationReference(evidence);
            transfer.setOriginatorVerifiedBy(verifier);
            transfer.setOriginatorVerifiedAt(verifiedAt);
        } else if ("BENEFICIARY".equalsIgnoreCase(request.party())) {
            transfer.setBeneficiaryVerification(status);
            transfer.setBeneficiaryVerificationReference(evidence);
            transfer.setBeneficiaryVerifiedBy(verifier);
            transfer.setBeneficiaryVerifiedAt(verifiedAt);
        }
        else throw new IllegalArgumentException("party must be ORIGINATOR or BENEFICIARY");
        refreshTransferStatus(transfer);
        updateTransactionTravelRule(transfer.getTransaction(), transfer.getStatus());
        return toTransfer(transferRepository.save(transfer));
    }

    @Transactional
    public TravelRuleTransferResponse transmit(Long transferId) {
        Long pspId = requiredPspId();
        TravelRuleTransfer transfer = transfer(transferId, pspId);
        if (transfer.getStatus() != TravelRuleTransferStatus.READY && transfer.getStatus() != TravelRuleTransferStatus.FAILED) {
            throw new IllegalStateException("Travel Rule transfer is not ready for transmission");
        }
        if (transfer.getBeneficiaryVasp() == null) throw new IllegalStateException("Beneficiary VASP is required");
        int attemptNumber = (int) attemptRepository.countByTransferId(transfer.getId()) + 1;
        String idempotencyKey = "TR-" + transfer.getId() + "-" + attemptNumber + "-" + transfer.getPayloadHash();
        Map<String, Object> payload = parsePayload(transfer.getEncryptedPayload());
        TravelRuleGatewayClient.TransmissionResult result = travelRuleClient.transmit(idempotencyKey,
                transfer.getTransaction().getExternalTransactionId(), transfer.getProtocol(),
                transfer.getBeneficiaryVasp().getLegalName(), payload);
        TravelRuleTransmissionAttempt attempt = new TravelRuleTransmissionAttempt();
        attempt.setPspId(pspId); attempt.setTransfer(transfer); attempt.setAttemptNumber(attemptNumber);
        attempt.setIdempotencyKey(idempotencyKey); attempt.setProtocol(transfer.getProtocol());
        attempt.setStatus(result.accepted() ? "ACCEPTED" : result.available() ? "FAILED" : "UNAVAILABLE");
        attempt.setProviderMessageId(result.messageId()); attempt.setResponseCode(result.responseCode());
        attempt.setFailureReason(result.failureReason()); attempt.setRetainUntil(transfer.getRetainUntil());
        attemptRepository.save(attempt);
        transfer.setTransmissionAttempts(attemptNumber);
        transfer.setProviderMessageId(result.messageId());
        if (result.accepted()) {
            transfer.setTransmittedAt(LocalDateTime.now());
            transfer.setStatus(result.acknowledged() ? TravelRuleTransferStatus.ACKNOWLEDGED : TravelRuleTransferStatus.TRANSMITTED);
            if (result.acknowledged()) transfer.setAcknowledgedAt(LocalDateTime.now());
            transfer.setFailureReason(null);
        } else {
            transfer.setStatus(TravelRuleTransferStatus.FAILED);
            transfer.setFailureReason(result.failureReason());
        }
        updateTransactionTravelRule(transfer.getTransaction(), transfer.getStatus());
        return toTransfer(transferRepository.save(transfer));
    }

    @Transactional(readOnly = true)
    public Page<TravelRuleTransferResponse> listTransfers(int page, int size) {
        return transferRepository.findByPspIdOrderByCreatedAtDesc(requiredPspId(),
                PageRequest.of(Math.max(page, 0), bounded(size))).map(this::toTransfer);
    }

    @Transactional
    public RegulatorGrantResponse createGrant(CreateRegulatorGrantRequest request) {
        Set<String> scopes = new LinkedHashSet<>(normalizedList(request.scopes()));
        if (!GRANT_SCOPES.containsAll(scopes)) throw new IllegalArgumentException("Unsupported regulator access scope");
        if (scopes.contains("TRAVEL_RULE_PII")
                && !scopes.contains("TRAVEL_RULE_READ") && !scopes.contains("TRANSACTIONS_READ")) {
            throw new IllegalArgumentException("TRAVEL_RULE_PII requires TRAVEL_RULE_READ or TRANSACTIONS_READ");
        }
        byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        String rawKey = "va_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        VirtualAssetRegulatorAccessGrant grant = new VirtualAssetRegulatorAccessGrant();
        grant.setPspId(requiredPspId()); grant.setRegulatorName(required(request.regulatorName(), "regulatorName"));
        grant.setJurisdiction(country(request.jurisdiction())); grant.setAccessKeyHash(hash(rawKey));
        grant.setScopes(List.copyOf(scopes)); grant.setAllowedIpAddresses(normalizedList(request.allowedIpAddresses()));
        grant.setExpiresAt(request.expiresAt()); grant.setCreatedBy(currentUsername());
        return toGrant(grantRepository.save(grant), rawKey);
    }

    @Transactional(readOnly = true)
    public List<RegulatorGrantResponse> listGrants() {
        return grantRepository.findByPspIdOrderByCreatedAtDesc(requiredPspId()).stream()
                .map(grant -> toGrant(grant, null)).toList();
    }

    @Transactional
    public void revokeGrant(Long id) {
        VirtualAssetRegulatorAccessGrant grant = grantRepository.findByIdAndPspId(id, requiredPspId())
                .orElseThrow(() -> new IllegalArgumentException("Regulator grant not found: " + id));
        grant.setRevokedAt(LocalDateTime.now()); grantRepository.save(grant);
    }

    @Transactional
    public Page<Map<String, Object>> regulatorTransactions(String rawKey, String sourceIp, String userAgent,
            LocalDateTime from, LocalDateTime to, int page, int size, boolean includePii) {
        validateDateWindow(from, to);
        VirtualAssetRegulatorAccessGrant grant = validateGrant(rawKey, sourceIp, "TRANSACTIONS_READ");
        if (includePii && !grant.getScopes().contains("TRAVEL_RULE_PII")) throw new SecurityException("Grant does not allow Travel Rule PII");
        Page<MultiAssetTransaction> transactions = transactionRepository
                .findByPspIdAndAssetClassAndExecutedAtBetweenOrderByExecutedAtDesc(grant.getPspId(), AssetClass.CRYPTO,
                        from, to, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)));
        Page<Map<String, Object>> result = transactions.map(transaction -> regulatorTransaction(transaction, includePii));
        auditRegulatorAccess(grant, "/regulator/virtual-assets/transactions",
                from + "|" + to + "|" + page + "|" + size + "|" + includePii,
                sourceIp, userAgent, result.getNumberOfElements());
        grant.setLastAccessedAt(LocalDateTime.now()); grantRepository.save(grant);
        return result;
    }

    @Transactional
    public Page<Map<String, Object>> regulatorWalletScreenings(String rawKey, String sourceIp, String userAgent,
            LocalDateTime from, LocalDateTime to, int page, int size) {
        validateDateWindow(from, to);
        VirtualAssetRegulatorAccessGrant grant = validateGrant(rawKey, sourceIp, "WALLET_SCREENING_READ");
        Page<WalletScreeningRecord> screenings = screeningRepository
                .findByPspIdAndScreenedAtBetweenOrderByScreenedAtDesc(grant.getPspId(), from, to,
                        PageRequest.of(Math.max(page, 0), externalPageSize(size)));
        Page<Map<String, Object>> result = screenings.map(this::regulatorWalletScreening);
        auditRegulatorAccess(grant, "/regulator/virtual-assets/wallet-screenings",
                from + "|" + to + "|" + page + "|" + size, sourceIp, userAgent, result.getNumberOfElements());
        grant.setLastAccessedAt(LocalDateTime.now()); grantRepository.save(grant);
        return result;
    }

    @Transactional
    public Page<Map<String, Object>> regulatorTravelRuleTransfers(String rawKey, String sourceIp, String userAgent,
            LocalDateTime from, LocalDateTime to, int page, int size, boolean includePii) {
        validateDateWindow(from, to);
        VirtualAssetRegulatorAccessGrant grant = validateGrant(rawKey, sourceIp, "TRAVEL_RULE_READ");
        if (includePii && !grant.getScopes().contains("TRAVEL_RULE_PII")) {
            throw new SecurityException("Grant does not allow Travel Rule PII");
        }
        Page<TravelRuleTransfer> transfers = transferRepository
                .findByPspIdAndCreatedAtBetweenOrderByCreatedAtDesc(grant.getPspId(), from, to,
                        PageRequest.of(Math.max(page, 0), externalPageSize(size)));
        Page<Map<String, Object>> result = transfers.map(transfer -> regulatorTravelRuleTransfer(transfer, includePii));
        auditRegulatorAccess(grant, "/regulator/virtual-assets/travel-rule-transfers",
                from + "|" + to + "|" + page + "|" + size + "|" + includePii,
                sourceIp, userAgent, result.getNumberOfElements());
        grant.setLastAccessedAt(LocalDateTime.now()); grantRepository.save(grant);
        return result;
    }

    private void screenVaspInternal(VaspDirectoryEntry vasp) {
        Map<String, ScreeningResult.EntityType> subjects = new LinkedHashMap<>();
        subjects.put(vasp.getLegalName(), ScreeningResult.EntityType.ORGANIZATION);
        if (vasp.getTradingName() != null && !vasp.getTradingName().equalsIgnoreCase(vasp.getLegalName())) {
            subjects.put(vasp.getTradingName(), ScreeningResult.EntityType.ORGANIZATION);
        }
        for (Map<String, Object> owner : vasp.getBeneficialOwnership()) {
            String name = firstMapText(owner, "fullName", "legalName", "name");
            if (name != null) subjects.putIfAbsent(name, ScreeningResult.EntityType.PERSON);
        }

        ScreeningResult.ScreeningStatus aggregate = ScreeningResult.ScreeningStatus.CLEAR;
        int totalMatches = 0;
        String provider = "AML_MICROSERVICE";
        VaspScreeningRecord alertEvidence = null;
        for (Map.Entry<String, ScreeningResult.EntityType> subject : subjects.entrySet()) {
            ScreeningResult result = sanctionsScreeningService.screenName(subject.getKey(), subject.getValue());
            ScreeningResult.ScreeningStatus status = result == null || result.getStatus() == null
                    ? ScreeningResult.ScreeningStatus.UNAVAILABLE : result.getStatus();
            int matchCount = result == null || result.getMatchCount() == null ? 0 : result.getMatchCount();
            totalMatches += matchCount;
            aggregate = strongerScreeningStatus(aggregate, status);
            String resultProvider = result == null || optional(result.getScreeningProvider()) == null
                    ? provider : result.getScreeningProvider();
            provider = resultProvider;

            VaspScreeningRecord record = new VaspScreeningRecord();
            record.setPspId(vasp.getPspId()); record.setVasp(vasp); record.setSubjectName(subject.getKey());
            record.setSubjectType(subject.getValue().name()); record.setProvider(resultProvider);
            record.setAvailable(status != ScreeningResult.ScreeningStatus.UNAVAILABLE); record.setStatus(status.name());
            record.setMatchCount(matchCount); record.setMatches(screeningMatches(result));
            Map<String, Object> evidence = new LinkedHashMap<>();
            if (result != null && result.getHighestMatchScore() != null) evidence.put("highestMatchScore", result.getHighestMatchScore());
            evidence.put("entityType", subject.getValue().name()); evidence.put("screeningStatus", status.name());
            record.setEvidence(evidence); record.setRetainUntil(LocalDate.now().plusYears(7));
            record = vaspScreeningRepository.save(record);
            if (status != ScreeningResult.ScreeningStatus.CLEAR) alertEvidence = record;
        }

        LocalDateTime now = LocalDateTime.now();
        vasp.setSanctionsStatus(aggregate.name()); vasp.setSanctionsProvider(provider);
        vasp.setSanctionsMatchCount(totalMatches); vasp.setSanctionsScreenedAt(now);
        vasp.setSanctionsNextScreeningAt(now.plusHours(
                aggregate == ScreeningResult.ScreeningStatus.UNAVAILABLE ? 1 : vaspScreeningIntervalHours));
        if (aggregate == ScreeningResult.ScreeningStatus.MATCH) {
            vasp.setRiskScore(100); vasp.setRiskLevel("CRITICAL"); vasp.setTransferDecision(VaspTransferDecision.PROHIBITED);
        } else if (aggregate == ScreeningResult.ScreeningStatus.POTENTIAL_MATCH) {
            vasp.setRiskScore(Math.max(vasp.getRiskScore(), 75)); vasp.setRiskLevel(riskLevel(vasp.getRiskScore()));
            if (vasp.getTransferDecision() == VaspTransferDecision.PERMITTED) vasp.setTransferDecision(VaspTransferDecision.REVIEW);
        } else if (aggregate == ScreeningResult.ScreeningStatus.UNAVAILABLE) {
            vasp.setRiskScore(Math.max(vasp.getRiskScore(), 60)); vasp.setRiskLevel(riskLevel(vasp.getRiskScore()));
            if (vasp.getTransferDecision() == VaspTransferDecision.PERMITTED) vasp.setTransferDecision(VaspTransferDecision.REVIEW);
        }
        vaspRepository.save(vasp);
        if (alertEvidence != null) createVaspScreeningAlert(vasp, alertEvidence);
    }

    private ScreeningResult.ScreeningStatus strongerScreeningStatus(ScreeningResult.ScreeningStatus current,
            ScreeningResult.ScreeningStatus candidate) {
        List<ScreeningResult.ScreeningStatus> order = List.of(ScreeningResult.ScreeningStatus.CLEAR,
                ScreeningResult.ScreeningStatus.UNAVAILABLE, ScreeningResult.ScreeningStatus.POTENTIAL_MATCH,
                ScreeningResult.ScreeningStatus.MATCH);
        return order.indexOf(candidate) > order.indexOf(current) ? candidate : current;
    }

    private List<Map<String, Object>> screeningMatches(ScreeningResult result) {
        if (result == null || result.getMatches() == null) return List.of();
        return objectMapper.convertValue(result.getMatches(), new TypeReference<>() {});
    }

    private String firstMapText(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
        }
        return null;
    }

    private void createVaspScreeningAlert(VaspDirectoryEntry vasp, VaspScreeningRecord evidence) {
        String reference = String.valueOf(vasp.getId());
        if (alertRepository.existsByPspIdAndSourceTypeAndSourceReference(vasp.getPspId(), "VASP_SANCTIONS_SCREENING", reference)) return;
        Alert alert = new Alert(); alert.setPspId(vasp.getPspId()); alert.setSourceType("VASP_SANCTIONS_SCREENING");
        alert.setSourceReference(reference); alert.setScore("MATCH".equals(vasp.getSanctionsStatus()) ? 1.0 : 0.6);
        alert.setAction("MATCH".equals(vasp.getSanctionsStatus()) ? "BLOCK" : "REVIEW"); alert.setStatus("open");
        alert.setSeverity("MATCH".equals(vasp.getSanctionsStatus()) ? "CRITICAL" : "WARN");
        alert.setReason("VASP sanctions screening " + vasp.getSanctionsStatus().toLowerCase(Locale.ROOT)
                + " for " + evidence.getSubjectName()); alertRepository.save(alert);
    }

    private WalletScreeningRecord screenInternal(CryptoWalletProfile wallet, WalletScreeningTrigger trigger,
            MultiAssetTransaction transaction) {
        BlockchainAnalyticsClient.WalletRiskResult result = blockchainClient.screenWallet(wallet.getWalletAddress(), wallet.getNetwork());
        WalletScreeningRecord record = persistScreening(wallet, wallet.getCustomer(), transaction, trigger,
                wallet.getWalletAddress(), wallet.getNetwork(), result);
        updateWalletProfile(wallet, record, result);
        if (transaction != null) attachScreeningSignal(transaction, wallet, record);
        if ((result.available() && result.riskScore() >= 50)
                || (!result.available() && trigger == WalletScreeningTrigger.PRE_WITHDRAWAL)) createWalletAlert(wallet, record);
        return record;
    }

    private WalletScreeningRecord persistScreening(CryptoWalletProfile wallet, MultiAssetCustomer customer,
            MultiAssetTransaction transaction, WalletScreeningTrigger trigger, String address, String network,
            BlockchainAnalyticsClient.WalletRiskResult result) {
        WalletScreeningRecord record = new WalletScreeningRecord();
        record.setPspId(customer.getPspId()); record.setWalletProfile(wallet); record.setCustomer(customer);
        record.setTransaction(transaction); record.setScreenedAddress(optional(address)); record.setNetwork(optional(network));
        record.setTriggerType(trigger); record.setProvider(result.provider()); record.setProviderReference(result.providerReference());
        record.setAvailable(result.available()); record.setRiskScore(result.available() ? result.riskScore() : null);
        record.setCategories(result.categories()); record.setDirectExposurePercent(result.directExposurePercent());
        record.setIndirectExposurePercent(result.indirectExposurePercent()); record.setMaximumExposureDepth(result.maximumExposureDepth());
        record.setAttributions(result.attributions()); record.setUnavailableReason(result.unavailableReason());
        Map<String, Object> evidence = new LinkedHashMap<>();
        if (address != null && !address.isBlank()) evidence.put("walletAddressHash", hash(address));
        if (network != null && !network.isBlank()) evidence.put("network", network);
        evidence.put("trigger", trigger.name()); record.setEvidence(evidence); record.setRetainUntil(LocalDate.now().plusYears(7));
        return screeningRepository.save(record);
    }

    private void updateWalletProfile(CryptoWalletProfile wallet, WalletScreeningRecord record,
            BlockchainAnalyticsClient.WalletRiskResult result) {
        wallet.setLastScreenedAt(record.getScreenedAt());
        wallet.setNextScreeningAt(LocalDateTime.now().plusHours(result.available() ? wallet.getScreeningIntervalHours() : 1));
        wallet.setLatestProvider(result.provider()); wallet.setLatestProviderReference(result.providerReference());
        wallet.setScreeningAvailable(result.available()); wallet.setLatestCategories(result.categories());
        wallet.setLatestRiskScore(result.available() ? result.riskScore() : null);
        wallet.setScreeningStatus(!result.available() ? "UNAVAILABLE" : result.riskScore() >= 80 ? "CRITICAL"
                : result.riskScore() >= 50 ? "HIGH_RISK" : "CLEAR");
        walletRepository.save(wallet);
    }

    private void attachScreeningSignal(MultiAssetTransaction transaction, CryptoWalletProfile wallet,
            WalletScreeningRecord screening) {
        int impact = !screening.isAvailable() ? 40 : screening.getRiskScore() >= 80 ? 65 : screening.getRiskScore() >= 50 ? 35 : 0;
        if (impact == 0) return;
        MultiAssetRiskSignal signal = new MultiAssetRiskSignal();
        signal.setTransaction(transaction); signal.setCustomer(transaction.getCustomer()); signal.setPspId(transaction.getPspId());
        signal.setSignalCode(!screening.isAvailable() ? "CRYPTO_SCREENING_UNAVAILABLE" : screening.getRiskScore() >= 80
                ? "CRYPTO_HIGH_RISK_WALLET" : "CRYPTO_ELEVATED_WALLET_RISK");
        signal.setSignalType(FinancialCrimeSignalType.CRYPTO_EXPOSURE); signal.setProductDomain(ProductDomain.VIRTUAL_ASSET);
        signal.setSeverity(impact >= 50 ? "CRITICAL" : "HIGH"); signal.setScoreImpact(impact);
        signal.setDescription(!screening.isAvailable() ? "Wallet screening provider was unavailable; no clean decision was made."
                : "Wallet exposure screening identified elevated virtual-asset risk.");
        signal.setEvidence(Map.of("walletProfileId", wallet.getId(), "screeningRecordId", screening.getId(),
                "trigger", screening.getTriggerType().name())); signalRepository.save(signal);
        int score = Math.min(100, transaction.getRiskScore() + impact); transaction.setRiskScore(score);
        transaction.setDecision(score >= 70 ? RiskDecision.BLOCK : score >= 40 ? RiskDecision.REVIEW : RiskDecision.ALERT);
        transactionRepository.save(transaction);
    }

    private void createWalletAlert(CryptoWalletProfile wallet, WalletScreeningRecord screening) {
        String reference = String.valueOf(screening.getId());
        if (alertRepository.existsByPspIdAndSourceTypeAndSourceReference(wallet.getPspId(), "CRYPTO_WALLET_SCREENING", reference)) return;
        Alert alert = new Alert(); alert.setPspId(wallet.getPspId()); alert.setMultiAssetCustomerId(wallet.getCustomer().getId());
        alert.setSourceType("CRYPTO_WALLET_SCREENING"); alert.setSourceReference(reference);
        alert.setScore(screening.isAvailable() ? screening.getRiskScore() / 100.0 : 0.4);
        alert.setAction(screening.isAvailable() && screening.getRiskScore() >= 80 ? "BLOCK" : "REVIEW");
        alert.setStatus("open"); alert.setSeverity(screening.isAvailable() && screening.getRiskScore() >= 80 ? "CRITICAL" : "WARN");
        alert.setReason(screening.isAvailable() ? "Crypto wallet exposure requires review" : "Pre-withdrawal wallet screening unavailable");
        alertRepository.save(alert);
    }

    private void evaluateTransfer(TravelRuleTransfer transfer, Map<String, Object> payload) {
        TravelRuleJurisdictionPolicy policy = transfer.getPolicy();
        if (policy == null) { transfer.setStatus(TravelRuleTransferStatus.PENDING_DATA); transfer.setFailureReason("No active jurisdiction policy"); return; }
        BigDecimal usd = usdValue(transfer.getTransaction());
        if (usd == null) { transfer.setStatus(TravelRuleTransferStatus.PENDING_DATA); transfer.setFailureReason("USD-equivalent value is missing"); return; }
        if (!policy.isAppliesToAllTransfers() && usd.compareTo(policy.getThresholdUsd()) < 0) {
            transfer.setStatus(TravelRuleTransferStatus.NOT_REQUIRED);
            transfer.setOriginatorVerification(IdentityVerificationStatus.NOT_REQUIRED);
            transfer.setBeneficiaryVerification(IdentityVerificationStatus.NOT_REQUIRED); transfer.setFailureReason(null); return;
        }
        List<String> missing = policy.getRequiredFields().stream().filter(field -> !hasValue(payload, field)).toList();
        if (!missing.isEmpty()) { transfer.setStatus(TravelRuleTransferStatus.PENDING_DATA); transfer.setFailureReason("Missing fields: " + String.join(", ", missing)); return; }
        if (transfer.getBeneficiaryVasp() != null && transfer.getBeneficiaryVasp().getTransferDecision() == VaspTransferDecision.PROHIBITED) {
            transfer.setStatus(TravelRuleTransferStatus.REJECTED); transfer.setFailureReason("Beneficiary VASP is prohibited"); return;
        }
        if (!policy.getAcceptedProtocols().isEmpty()
                && (transfer.getProtocol() == null || policy.getAcceptedProtocols().stream().noneMatch(p -> p.equalsIgnoreCase(transfer.getProtocol())))) {
            transfer.setStatus(TravelRuleTransferStatus.PENDING_DATA); transfer.setFailureReason("Accepted Travel Rule protocol is required"); return;
        }
        transfer.setOriginatorVerification(policy.isVerifyOriginator() ? IdentityVerificationStatus.PENDING : IdentityVerificationStatus.NOT_REQUIRED);
        transfer.setBeneficiaryVerification(policy.isVerifyBeneficiary() ? IdentityVerificationStatus.PENDING : IdentityVerificationStatus.NOT_REQUIRED);
        refreshTransferStatus(transfer); transfer.setFailureReason(null);
    }

    private void refreshTransferStatus(TravelRuleTransfer transfer) {
        if (transfer.getStatus() == TravelRuleTransferStatus.NOT_REQUIRED) return;
        if (transfer.getStatus() == TravelRuleTransferStatus.REJECTED
                && transfer.getFailureReason() != null
                && transfer.getFailureReason().contains("VASP is prohibited")) return;
        if (transfer.getOriginatorVerification() == IdentityVerificationStatus.FAILED
                || transfer.getBeneficiaryVerification() == IdentityVerificationStatus.FAILED) {
            transfer.setStatus(TravelRuleTransferStatus.REJECTED); transfer.setFailureReason("Identity verification failed"); return;
        }
        boolean originatorReady = transfer.getOriginatorVerification() == IdentityVerificationStatus.VERIFIED
                || transfer.getOriginatorVerification() == IdentityVerificationStatus.NOT_REQUIRED;
        boolean beneficiaryReady = transfer.getBeneficiaryVerification() == IdentityVerificationStatus.VERIFIED
                || transfer.getBeneficiaryVerification() == IdentityVerificationStatus.NOT_REQUIRED;
        transfer.setStatus(originatorReady && beneficiaryReady ? TravelRuleTransferStatus.READY
                : TravelRuleTransferStatus.PENDING_VERIFICATION);
        transfer.setFailureReason(null);
    }

    private void updateTransactionTravelRule(MultiAssetTransaction transaction, TravelRuleTransferStatus status) {
        transaction.setTravelRuleRequired(status != TravelRuleTransferStatus.NOT_REQUIRED);
        transaction.setTravelRuleStatus(switch (status) {
            case NOT_REQUIRED -> TravelRuleStatus.NOT_REQUIRED;
            case ACKNOWLEDGED, TRANSMITTED -> TravelRuleStatus.COMPLETE;
            case PENDING_VERIFICATION, READY, TRANSMISSION_PENDING -> TravelRuleStatus.PENDING_VERIFICATION;
            default -> TravelRuleStatus.INCOMPLETE;
        });
        transactionRepository.save(transaction);
    }

    private VirtualAssetRegulatorAccessGrant validateGrant(String rawKey, String sourceIp, String scope) {
        if (rawKey == null || rawKey.isBlank()) throw new SecurityException("Regulator access key is required");
        VirtualAssetRegulatorAccessGrant grant = grantRepository.findByAccessKeyHash(hash(rawKey))
                .orElseThrow(() -> new SecurityException("Invalid regulator access key"));
        LocalDateTime now = LocalDateTime.now();
        if (grant.getRevokedAt() != null || !grant.getExpiresAt().isAfter(now)) throw new SecurityException("Regulator access grant is inactive");
        if (!grant.getScopes().contains(scope)) throw new SecurityException("Regulator access scope is not granted");
        if (!grant.getAllowedIpAddresses().isEmpty() && !grant.getAllowedIpAddresses().contains(sourceIp)) {
            throw new SecurityException("Source IP is not allowed");
        }
        return grant;
    }

    private void auditRegulatorAccess(VirtualAssetRegulatorAccessGrant grant, String endpoint, String query,
            String sourceIp, String userAgent, int rows) {
        VirtualAssetRegulatorAccessLog access = new VirtualAssetRegulatorAccessLog(); access.setGrant(grant);
        access.setPspId(grant.getPspId()); access.setEndpoint(endpoint); access.setQueryHash(hash(query));
        access.setSourceIp(sourceIp); access.setUserAgent(userAgent); access.setRowsReturned(rows);
        access.setRetainUntil(LocalDate.now().plusYears(7)); accessLogRepository.save(access);
    }

    private Map<String, Object> regulatorTransaction(MultiAssetTransaction transaction, boolean includePii) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("transactionId", transaction.getId()); row.put("externalTransactionId", transaction.getExternalTransactionId());
        row.put("customerId", transaction.getCustomer().getExternalCustomerId()); row.put("amount", transaction.getAmount());
        row.put("fiatEquivalentUsd", transaction.getFiatEquivalentUsd()); row.put("currency", transaction.getCurrency());
        row.put("assetSymbol", transaction.getAssetSymbol()); row.put("sourceNetwork", transaction.getSourceNetwork());
        row.put("destinationNetwork", transaction.getDestinationNetwork()); row.put("counterpartyReference", transaction.getCounterpartyReference());
        row.put("riskScore", transaction.getRiskScore()); row.put("decision", transaction.getDecision());
        row.put("travelRuleStatus", transaction.getTravelRuleStatus()); row.put("executedAt", transaction.getExecutedAt());
        transferRepository.findByPspIdAndTransactionId(transaction.getPspId(), transaction.getId()).ifPresent(transfer -> {
            row.put("travelRuleTransferId", transfer.getId()); row.put("travelRuleTransferStatus", transfer.getStatus());
            row.put("payloadHash", transfer.getPayloadHash());
            if (includePii && transfer.getEncryptedPayload() != null) row.put("travelRulePayload", parsePayload(transfer.getEncryptedPayload()));
        });
        return row;
    }

    private Map<String, Object> regulatorWalletScreening(WalletScreeningRecord screening) {
        CryptoWalletProfile wallet = screening.getWalletProfile();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("screeningRecordId", screening.getId()); row.put("walletProfileId", wallet == null ? null : wallet.getId());
        row.put("customerId", screening.getCustomer().getExternalCustomerId()); row.put("walletAddress", screening.getScreenedAddress());
        row.put("network", screening.getNetwork()); row.put("vasp", wallet == null || wallet.getVasp() == null ? null : wallet.getVasp().getLegalName());
        row.put("trigger", screening.getTriggerType()); row.put("provider", screening.getProvider());
        row.put("providerReference", screening.getProviderReference()); row.put("available", screening.isAvailable());
        row.put("riskScore", screening.getRiskScore()); row.put("categories", screening.getCategories());
        row.put("directExposurePercent", screening.getDirectExposurePercent());
        row.put("indirectExposurePercent", screening.getIndirectExposurePercent());
        row.put("maximumExposureDepth", screening.getMaximumExposureDepth()); row.put("attributions", screening.getAttributions());
        row.put("unavailableReason", screening.getUnavailableReason()); row.put("screenedAt", screening.getScreenedAt());
        row.put("retainUntil", screening.getRetainUntil());
        return row;
    }

    private Map<String, Object> regulatorTravelRuleTransfer(TravelRuleTransfer transfer, boolean includePii) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("travelRuleTransferId", transfer.getId());
        row.put("transactionId", transfer.getTransaction().getExternalTransactionId());
        row.put("policyCode", transfer.getPolicy() == null ? null : transfer.getPolicy().getPolicyCode());
        row.put("jurisdiction", transfer.getJurisdiction()); row.put("status", transfer.getStatus());
        row.put("originatorVasp", transfer.getOriginatorVasp() == null ? null : transfer.getOriginatorVasp().getLegalName());
        row.put("beneficiaryVasp", transfer.getBeneficiaryVasp() == null ? null : transfer.getBeneficiaryVasp().getLegalName());
        row.put("originatorVerification", transfer.getOriginatorVerification());
        row.put("originatorVerificationReference", transfer.getOriginatorVerificationReference());
        row.put("originatorVerifiedBy", transfer.getOriginatorVerifiedBy()); row.put("originatorVerifiedAt", transfer.getOriginatorVerifiedAt());
        row.put("beneficiaryVerification", transfer.getBeneficiaryVerification());
        row.put("beneficiaryVerificationReference", transfer.getBeneficiaryVerificationReference());
        row.put("beneficiaryVerifiedBy", transfer.getBeneficiaryVerifiedBy()); row.put("beneficiaryVerifiedAt", transfer.getBeneficiaryVerifiedAt());
        row.put("protocol", transfer.getProtocol()); row.put("payloadHash", transfer.getPayloadHash());
        row.put("providerMessageId", transfer.getProviderMessageId()); row.put("transmissionAttempts", transfer.getTransmissionAttempts());
        row.put("transmittedAt", transfer.getTransmittedAt()); row.put("acknowledgedAt", transfer.getAcknowledgedAt());
        row.put("failureReason", transfer.getFailureReason()); row.put("retainUntil", transfer.getRetainUntil());
        row.put("createdAt", transfer.getCreatedAt());
        if (includePii && transfer.getEncryptedPayload() != null) row.put("travelRulePayload", parsePayload(transfer.getEncryptedPayload()));
        return row;
    }

    private VaspDirectoryEntry vasp(Long id, Long pspId) { return vaspRepository.findByIdAndPspId(id, pspId).orElseThrow(() -> new IllegalArgumentException("VASP not found: " + id)); }
    private TravelRuleTransfer transfer(Long id, Long pspId) { return transferRepository.findByIdAndPspId(id, pspId).orElseThrow(() -> new IllegalArgumentException("Travel Rule transfer not found: " + id)); }
    private BigDecimal usdValue(MultiAssetTransaction transaction) { if (transaction.getFiatEquivalentUsd() != null) return transaction.getFiatEquivalentUsd(); return Set.of("USD", "USDT", "USDC").contains(transaction.getCurrency().toUpperCase(Locale.ROOT)) ? transaction.getAmount() : null; }
    private boolean hasValue(Map<String, Object> payload, String path) { Object current = payload; for (String part : path.split("\\.")) { if (!(current instanceof Map<?, ?> map)) return false; current = map.get(part); } return current != null && !(current instanceof String text && text.isBlank()); }
    private String json(Map<String, Object> payload) { try { return objectMapper.writeValueAsString(payload); } catch (Exception e) { throw new IllegalArgumentException("Travel Rule payload is not serializable", e); } }
    private Map<String, Object> parsePayload(String payload) { try { return objectMapper.readValue(payload, new TypeReference<>() {}); } catch (Exception e) { throw new IllegalStateException("Encrypted Travel Rule payload cannot be parsed", e); } }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); } }
    private Long requiredPspId() { Long id = pspIsolationService.getCurrentUserPspId(); if (id == null) throw new SecurityException("Authenticated user has no PSP context"); return id; }
    private String currentUsername() { var auth = SecurityContextHolder.getContext().getAuthentication(); return auth == null ? "SYSTEM" : auth.getName(); }
    private int bounded(int size) { return Math.min(Math.max(size, 1), 100); }
    private int externalPageSize(int size) { return Math.min(Math.max(size, 1), 200); }
    private void validateDateWindow(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || from.isAfter(to)) throw new IllegalArgumentException("from must be before or equal to to");
    }
    private String required(String value, String field) { String result = optional(value); if (result == null) throw new IllegalArgumentException(field + " is required"); return result; }
    private String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String country(String value) { String result = required(value, "jurisdiction").toUpperCase(Locale.ROOT); if (result.length() < 2 || result.length() > 3) throw new IllegalArgumentException("jurisdiction must be a 2 or 3 character country code"); return result; }
    private List<String> normalizedList(List<String> values) { if (values == null) return new ArrayList<>(); return values.stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isBlank()).map(v -> v.toUpperCase(Locale.ROOT)).distinct().toList(); }
    private List<String> normalizedFields(List<String> values) { if (values == null) return new ArrayList<>(); return values.stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isBlank()).distinct().toList(); }
    private List<Map<String, Object>> copyMaps(List<Map<String, Object>> values) { return values == null ? new ArrayList<>() : values.stream().map(LinkedHashMap::new).map(map -> (Map<String, Object>) map).toList(); }
    private String riskLevel(int score) { return score >= 80 ? "CRITICAL" : score >= 60 ? "HIGH" : score >= 30 ? "MEDIUM" : "LOW"; }

    private VaspResponse toVasp(VaspDirectoryEntry v) { return new VaspResponse(v.getId(), v.getLegalName(), v.getTradingName(), v.getJurisdiction(), v.getRegulatorName(), v.getLicenceNumber(), v.getLicenceStatus(), v.getLicenceValidUntil(), v.getRegistrationNumber(), v.getWebsite(), v.getTravelRuleProtocols(), v.getSanctionsStatus(), v.getSanctionsScreenedAt(), v.getSanctionsProvider(), v.getSanctionsMatchCount(), v.getSanctionsNextScreeningAt(), v.getBeneficialOwnership(), v.getWalletClusters(), v.getRiskScore(), v.getRiskLevel(), v.getTransferDecision(), v.getReviewNotes(), v.getLastReviewedAt(), v.getNextReviewAt()); }
    private VaspScreeningResponse toVaspScreening(VaspScreeningRecord s) { return new VaspScreeningResponse(s.getId(), s.getVasp().getId(), s.getVasp().getLegalName(), s.getSubjectName(), s.getSubjectType(), s.getProvider(), s.isAvailable(), s.getStatus(), s.getMatchCount(), s.getMatches(), s.getEvidence(), s.getScreenedAt(), s.getRetainUntil()); }
    private WalletProfileResponse toWallet(CryptoWalletProfile w) { return new WalletProfileResponse(w.getId(), w.getAssetAccount().getId(), w.getCustomer().getId(), w.getCustomer().getDisplayName(), w.getVasp() == null ? null : w.getVasp().getId(), w.getVasp() == null ? null : w.getVasp().getLegalName(), w.getWalletAddress(), w.getNetwork(), w.getAddressLabel(), w.getOwnershipType(), w.getStatus(), w.getScreeningIntervalHours(), w.getLastScreenedAt(), w.getNextScreeningAt(), w.getLatestRiskScore(), w.getLatestCategories(), w.getLatestProvider(), w.getLatestProviderReference(), w.isScreeningAvailable(), w.getScreeningStatus()); }
    private WalletScreeningResponse toScreening(WalletScreeningRecord s) { return new WalletScreeningResponse(s.getId(), s.getWalletProfile() == null ? null : s.getWalletProfile().getId(), s.getCustomer().getId(), s.getCustomer().getDisplayName(), s.getTransaction() == null ? null : s.getTransaction().getId(), s.getScreenedAddress(), s.getNetwork(), s.getTriggerType(), s.getProvider(), s.getProviderReference(), s.isAvailable(), s.getRiskScore(), s.getCategories(), s.getDirectExposurePercent(), s.getIndirectExposurePercent(), s.getMaximumExposureDepth(), s.getAttributions(), s.getEvidence(), s.getUnavailableReason(), s.getScreenedAt(), s.getRetainUntil()); }
    private TravelRulePolicyResponse toPolicy(TravelRuleJurisdictionPolicy p) { return new TravelRulePolicyResponse(p.getId(), p.getJurisdiction(), p.getPolicyCode(), p.getThresholdUsd(), p.isAppliesToAllTransfers(), p.getRequiredFields(), p.isVerifyOriginator(), p.isVerifyBeneficiary(), p.getAcceptedProtocols(), p.getRetentionYears(), p.getEffectiveFrom(), p.getEffectiveUntil(), p.isEnabled(), p.getLegalReference()); }
    private TravelRuleTransferResponse toTransfer(TravelRuleTransfer t) { return new TravelRuleTransferResponse(t.getId(), t.getTransaction().getId(), t.getTransaction().getExternalTransactionId(), t.getPolicy() == null ? null : t.getPolicy().getId(), t.getPolicy() == null ? null : t.getPolicy().getPolicyCode(), t.getOriginatorVasp() == null ? null : t.getOriginatorVasp().getId(), t.getOriginatorVasp() == null ? null : t.getOriginatorVasp().getLegalName(), t.getBeneficiaryVasp() == null ? null : t.getBeneficiaryVasp().getId(), t.getBeneficiaryVasp() == null ? null : t.getBeneficiaryVasp().getLegalName(), t.getJurisdiction(), t.getStatus(), t.getOriginatorVerification(), t.getBeneficiaryVerification(), t.getOriginatorVerificationReference(), t.getOriginatorVerifiedBy(), t.getOriginatorVerifiedAt(), t.getBeneficiaryVerificationReference(), t.getBeneficiaryVerifiedBy(), t.getBeneficiaryVerifiedAt(), t.getProtocol(), t.getPayloadHash(), t.getProviderMessageId(), t.getTransmissionAttempts(), t.getTransmittedAt(), t.getAcknowledgedAt(), t.getFailureReason(), t.getRetainUntil(), t.getCreatedAt()); }
    private RegulatorGrantResponse toGrant(VirtualAssetRegulatorAccessGrant g, String key) { return new RegulatorGrantResponse(g.getId(), g.getRegulatorName(), g.getJurisdiction(), g.getScopes(), g.getAllowedIpAddresses(), g.getExpiresAt(), g.getRevokedAt(), g.getLastAccessedAt(), g.getCreatedBy(), g.getCreatedAt(), key); }
}
