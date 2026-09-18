package com.posgateway.aml.service.limits;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.limits.*;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.limits.*;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for managing Limits & AML Management
 */
@Service
public class LimitsManagementService {

    public static final String AML_TRANSACTION_LIMIT = "AML_TRANSACTION";
    public static final String AML_DAILY_LIMIT = "AML_DAILY";

    private final MerchantTransactionLimitRepository merchantLimitRepository;
    private final GlobalLimitRepository globalLimitRepository;
    private final RiskThresholdRepository riskThresholdRepository;
    private final VelocityRuleRepository velocityRuleRepository;
    private final CountryComplianceRuleRepository countryComplianceRepository;
    private final MerchantRepository merchantRepository;
    private final TransactionRepository transactionRepository;
    private final PspIsolationService pspIsolationService;

    @Autowired
    public LimitsManagementService(
            MerchantTransactionLimitRepository merchantLimitRepository,
            GlobalLimitRepository globalLimitRepository,
            RiskThresholdRepository riskThresholdRepository,
            VelocityRuleRepository velocityRuleRepository,
            CountryComplianceRuleRepository countryComplianceRepository,
            MerchantRepository merchantRepository,
            TransactionRepository transactionRepository,
            PspIsolationService pspIsolationService) {
        this.merchantLimitRepository = merchantLimitRepository;
        this.globalLimitRepository = globalLimitRepository;
        this.riskThresholdRepository = riskThresholdRepository;
        this.velocityRuleRepository = velocityRuleRepository;
        this.countryComplianceRepository = countryComplianceRepository;
        this.merchantRepository = merchantRepository;
        this.transactionRepository = transactionRepository;
        this.pspIsolationService = pspIsolationService;
    }

    /**
     * Check if a rule is owned by super admin (pspId is null)
     */
    private boolean isSuperAdminRule(Long pspId) {
        return pspId == null;
    }

    private User requireCurrentUser() {
        User user = pspIsolationService.getCurrentUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        return user;
    }

    private Long currentScopePspId(User user) {
        return pspIsolationService.isPlatformAdministrator(user)
                ? null
                : pspIsolationService.getCurrentUserPspId();
    }

    private void validateMerchantAccess(Merchant merchant) {
        Long merchantPspId = merchant.getPsp() == null ? null : merchant.getPsp().getPspId();
        pspIsolationService.validatePspAccess(merchantPspId);
    }

    private <T> List<T> inheritedAndOwned(List<T> inherited, List<T> owned) {
        List<T> result = new ArrayList<>(owned.size() + inherited.size());
        result.addAll(owned);
        result.addAll(inherited);
        return result;
    }

    /**
     * Validate that current user can modify a rule
     */
    private void validateRuleModification(Long rulePspId, User currentUser) {
        if (isSuperAdminRule(rulePspId)) {
            // Only super admin can modify super admin rules
            if (!pspIsolationService.isPlatformAdministrator(currentUser)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Cannot modify super admin owned rules");
            }
        } else {
            // PSP users can only modify their own PSP's rules
            if (!pspIsolationService.isPlatformAdministrator(currentUser)) {
                Long userPspId = pspIsolationService.getCurrentUserPspId();
                if (userPspId == null || !userPspId.equals(rulePspId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Cannot modify rules from another PSP");
                }
            }
        }
    }

    // Merchant Limits
    @Transactional
    public MerchantTransactionLimit createOrUpdateMerchantLimit(Long merchantId, MerchantTransactionLimit limit, Long userId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found"));
        validateMerchantAccess(merchant);

        MerchantTransactionLimit existing = merchantLimitRepository.findByMerchant_MerchantId(merchantId)
                .orElse(new MerchantTransactionLimit());

        existing.setMerchant(merchant);
        existing.setDailyLimit(limit.getDailyLimit());
        existing.setWeeklyLimit(limit.getWeeklyLimit());
        existing.setMonthlyLimit(limit.getMonthlyLimit());
        existing.setPerTransactionLimit(limit.getPerTransactionLimit());
        existing.setStatus(limit.getStatus());
        existing.setUpdatedBy(userId);

        return merchantLimitRepository.save(existing);
    }

    public List<MerchantTransactionLimit> getAllMerchantLimits() {
        User user = requireCurrentUser();
        Long pspId = currentScopePspId(user);
        return pspId == null ? merchantLimitRepository.findAll()
                : merchantLimitRepository.findByMerchant_Psp_PspId(pspId);
    }

    public Page<MerchantTransactionLimit> getAllMerchantLimits(Pageable pageable) {
        User user = requireCurrentUser();
        Long pspId = currentScopePspId(user);
        return pspId == null ? merchantLimitRepository.findAll(pageable)
                : merchantLimitRepository.findByMerchant_Psp_PspId(pspId, pageable);
    }

    public MerchantTransactionLimit getMerchantLimit(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found"));
        validateMerchantAccess(merchant);
        return merchantLimitRepository.findByMerchant_MerchantId(merchantId).orElse(null);
    }

    // Global Limits
    @Transactional
    public GlobalLimit createGlobalLimit(GlobalLimit limit, Long userId) {
        User user = requireCurrentUser();
        Long pspId = currentScopePspId(user);
        Optional<GlobalLimit> existing = pspId == null
                ? globalLimitRepository.findByNameAndPspIdIsNull(limit.getName())
                : globalLimitRepository.findByNameAndPspId(limit.getName(), pspId);
        if (existing.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A limit with this name already exists in the current PSP scope");
        }
        limit.setPspId(pspId);
        limit.setCreatedBy(userId);
        return globalLimitRepository.save(limit);
    }

    @Transactional
    public GlobalLimit updateGlobalLimit(Long id, GlobalLimit limit, Long userId) {
        GlobalLimit existing = globalLimitRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Global limit not found"));
        validateRuleModification(existing.getPspId(), requireCurrentUser());
        existing.setName(limit.getName());
        existing.setDescription(limit.getDescription());
        existing.setLimitType(limit.getLimitType());
        existing.setLimitValue(limit.getLimitValue());
        existing.setPeriod(limit.getPeriod());
        existing.setStatus(limit.getStatus());
        existing.setUpdatedBy(userId);
        return globalLimitRepository.save(existing);
    }

    public List<GlobalLimit> getAllGlobalLimits() {
        User user = requireCurrentUser();
        Long pspId = currentScopePspId(user);
        return pspId == null ? globalLimitRepository.findAll()
                : inheritedAndOwned(globalLimitRepository.findByPspIdIsNull(), globalLimitRepository.findByPspId(pspId));
    }

    @Transactional
    public void deleteGlobalLimit(Long id) {
        GlobalLimit existing = globalLimitRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Global limit not found"));
        validateRuleModification(existing.getPspId(), requireCurrentUser());
        globalLimitRepository.deleteById(id);
    }

    @Transactional
    public List<GlobalLimit> upsertAmlLimits(BigDecimal transactionLimit, BigDecimal dailyLimit, Long userId) {
        User user = requireCurrentUser();
        Long pspId = currentScopePspId(user);
        List<GlobalLimit> saved = new ArrayList<>();
        if (transactionLimit != null) {
            saved.add(upsertNamedLimit(AML_TRANSACTION_LIMIT, "Maximum amount per transaction",
                    "VOLUME", "TRANSACTION", transactionLimit, userId, pspId));
        }
        if (dailyLimit != null) {
            saved.add(upsertNamedLimit(AML_DAILY_LIMIT, "Maximum PSP transaction volume per day",
                    "VOLUME", "DAY", dailyLimit, userId, pspId));
        }
        if (saved.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one AML limit is required");
        }
        return saved;
    }

    public Map<String, BigDecimal> getAmlLimits() {
        User user = requireCurrentUser();
        Long pspId = currentScopePspId(user);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        effectiveGlobalLimit(AML_TRANSACTION_LIMIT, pspId)
                .ifPresent(limit -> result.put("transactionLimit", limit.getLimitValue()));
        effectiveGlobalLimit(AML_DAILY_LIMIT, pspId)
                .ifPresent(limit -> result.put("dailyLimit", limit.getLimitValue()));
        return result;
    }

    private GlobalLimit upsertNamedLimit(String name, String description, String type, String period,
            BigDecimal value, Long userId, Long pspId) {
        if (value.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " must be greater than zero");
        }
        GlobalLimit limit = (pspId == null
                ? globalLimitRepository.findByNameAndPspIdIsNull(name)
                : globalLimitRepository.findByNameAndPspId(name, pspId)).orElseGet(GlobalLimit::new);
        boolean created = limit.getId() == null;
        limit.setName(name);
        limit.setDescription(description);
        limit.setLimitType(type);
        limit.setPeriod(period);
        limit.setLimitValue(value);
        limit.setStatus("ACTIVE");
        limit.setPspId(pspId);
        if (created) limit.setCreatedBy(userId);
        else limit.setUpdatedBy(userId);
        return globalLimitRepository.save(limit);
    }

    private Optional<GlobalLimit> effectiveGlobalLimit(String name, Long pspId) {
        if (pspId != null) {
            Optional<GlobalLimit> owned = globalLimitRepository.findByNameAndPspId(name, pspId);
            if (owned.isPresent()) return owned;
        }
        return globalLimitRepository.findByNameAndPspIdIsNull(name);
    }

    // Risk Thresholds
    @Transactional
    public RiskThreshold createOrUpdateRiskThreshold(RiskThreshold threshold, Long userId) {
        User currentUser = pspIsolationService.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        Long pspId = currentScopePspId(currentUser);
        RiskThreshold existing = (pspId == null
                ? riskThresholdRepository.findByRiskLevelAndPspIdIsNull(threshold.getRiskLevel())
                : riskThresholdRepository.findByRiskLevelAndPspId(threshold.getRiskLevel(), pspId))
                .orElse(new RiskThreshold());

        // If updating existing threshold, validate user can modify it
        if (existing.getId() != null) {
            validateRuleModification(existing.getPspId(), currentUser);
        }

        existing.setRiskLevel(threshold.getRiskLevel());
        existing.setDescription(threshold.getDescription());
        existing.setDailyLimit(threshold.getDailyLimit());
        existing.setPerTransactionLimit(threshold.getPerTransactionLimit());
        existing.setVelocityLimit(threshold.getVelocityLimit());
        existing.setStatus(threshold.getStatus());
        existing.setUpdatedBy(userId);

        if (existing.getId() == null) {
            existing.setCreatedBy(userId);
            // Set PSP ID for new thresholds
            existing.setPspId(pspId);
        }

        // Update merchant count
        List<Merchant> scopedMerchants = pspId == null
                ? merchantRepository.findAll()
                : merchantRepository.findByPspPspId(pspId);
        long merchantCount = scopedMerchants.stream()
                .filter(m -> {
                    String merchantRiskLevel = m.getRiskLevel();
                    String thresholdRiskLevel = threshold.getRiskLevel();
                    return merchantRiskLevel != null && merchantRiskLevel.equalsIgnoreCase(thresholdRiskLevel);
                })
                .count();
        existing.setMerchantCount((int) merchantCount);

        return riskThresholdRepository.save(existing);
    }

    public List<RiskThreshold> getAllRiskThresholds() {
        User user = requireCurrentUser();
        Long pspId = currentScopePspId(user);
        return pspId == null ? riskThresholdRepository.findAll()
                : inheritedAndOwned(riskThresholdRepository.findByPspIdIsNull(), riskThresholdRepository.findByPspId(pspId));
    }

    // Velocity Rules
    @Transactional
    public VelocityRule createVelocityRule(VelocityRule rule, Long userId) {
        User currentUser = pspIsolationService.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        Long pspId = currentScopePspId(currentUser);
        Optional<VelocityRule> duplicate = pspId == null
                ? velocityRuleRepository.findByRuleNameAndPspIdIsNull(rule.getRuleName())
                : velocityRuleRepository.findByRuleNameAndPspId(rule.getRuleName(), pspId);
        if (duplicate.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A velocity rule with this name already exists in the current PSP scope");
        }
        rule.setCreatedBy(userId);
        rule.setPspId(pspId);
        return velocityRuleRepository.save(rule);
    }

    @Transactional
    public VelocityRule updateVelocityRule(Long id, VelocityRule rule, Long userId) {
        User currentUser = pspIsolationService.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        VelocityRule existing = velocityRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Velocity rule not found"));

        // Validate user can modify this rule
        validateRuleModification(existing.getPspId(), currentUser);

        existing.setRuleName(rule.getRuleName());
        existing.setDescription(rule.getDescription());
        existing.setMaxTransactions(rule.getMaxTransactions());
        existing.setMaxAmount(rule.getMaxAmount());
        existing.setTimeWindowMinutes(rule.getTimeWindowMinutes());
        existing.setRiskLevel(rule.getRiskLevel());
        existing.setStatus(rule.getStatus());
        existing.setUpdatedBy(userId);
        return velocityRuleRepository.save(existing);
    }

    public List<VelocityRule> getAllVelocityRules() {
        User user = requireCurrentUser();
        Long pspId = currentScopePspId(user);
        return pspId == null ? velocityRuleRepository.findAll()
                : inheritedAndOwned(velocityRuleRepository.findByPspIdIsNull(), velocityRuleRepository.findByPspId(pspId));
    }

    @Transactional
    public void deleteVelocityRule(Long id) {
        User currentUser = pspIsolationService.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        VelocityRule existing = velocityRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Velocity rule not found"));

        // Validate user can delete this rule
        validateRuleModification(existing.getPspId(), currentUser);

        velocityRuleRepository.deleteById(id);
    }

    // Country Compliance
    @Transactional
    public CountryComplianceRule createOrUpdateCountryCompliance(CountryComplianceRule rule, Long userId) {
        User user = requireCurrentUser();
        Long pspId = currentScopePspId(user);
        CountryComplianceRule existing = (pspId == null
                ? countryComplianceRepository.findByCountryCodeAndPspIdIsNull(rule.getCountryCode())
                : countryComplianceRepository.findByCountryCodeAndPspId(rule.getCountryCode(), pspId))
                .orElse(new CountryComplianceRule());

        existing.setCountryCode(rule.getCountryCode());
        existing.setCountryName(rule.getCountryName());
        existing.setComplianceRequirements(rule.getComplianceRequirements());
        existing.setTransactionRestrictions(rule.getTransactionRestrictions());
        existing.setRequiredDocumentation(rule.getRequiredDocumentation());
        existing.setStatus(rule.getStatus());
        existing.setUpdatedBy(userId);

        if (existing.getId() == null) {
            existing.setCreatedBy(userId);
            existing.setPspId(pspId);
        } else {
            validateRuleModification(existing.getPspId(), user);
        }

        return countryComplianceRepository.save(existing);
    }

    public List<CountryComplianceRule> getAllCountryComplianceRules() {
        User user = requireCurrentUser();
        Long pspId = currentScopePspId(user);
        return pspId == null ? countryComplianceRepository.findAll()
                : inheritedAndOwned(countryComplianceRepository.findByPspIdIsNull(), countryComplianceRepository.findByPspId(pspId));
    }

    // Dashboard Statistics
    public Map<String, Object> getDashboardStats(Long pspId) {
        User user = requireCurrentUser();
        Long scopedPspId = currentScopePspId(user);
        if (!pspIsolationService.isPlatformAdministrator(user)) {
            pspId = scopedPspId;
        }
        Map<String, Object> stats = new HashMap<>();
        
        // Get merchants for this PSP
        List<Merchant> merchants;
        if (pspId != null) {
            merchants = merchantRepository.findByPspPspId(pspId);
        } else {
            merchants = merchantRepository.findAll();
        }
        
        // Active Merchants
        long activeMerchants = merchants.stream()
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .count();
        stats.put("activeMerchants", activeMerchants);

        // Calculate Daily Transaction Limit (sum of all merchant daily limits for this PSP)
        BigDecimal dailyTransactionLimit = BigDecimal.ZERO;
        List<MerchantTransactionLimit> merchantLimits;
        if (pspId != null) {
            merchantLimits = merchantLimitRepository.findByMerchant_Psp_PspId(pspId);
        } else {
            merchantLimits = merchantLimitRepository.findAll();
        }
        
        for (MerchantTransactionLimit limit : merchantLimits) {
            if (limit.getDailyLimit() != null && "ACTIVE".equals(limit.getStatus())) {
                dailyTransactionLimit = dailyTransactionLimit.add(limit.getDailyLimit());
            }
        }
        stats.put("dailyTransactionLimit", dailyTransactionLimit);

        // Calculate Monthly Volume Cap (sum of all merchant monthly limits for this PSP)
        BigDecimal monthlyVolumeCap = BigDecimal.ZERO;
        for (MerchantTransactionLimit limit : merchantLimits) {
            if (limit.getMonthlyLimit() != null && "ACTIVE".equals(limit.getStatus())) {
                monthlyVolumeCap = monthlyVolumeCap.add(limit.getMonthlyLimit());
            }
        }
        stats.put("monthlyVolumeCap", monthlyVolumeCap);

        // Calculate High-Risk Threshold (get highest risk threshold from active risk thresholds)
        List<RiskThreshold> visibleThresholds = pspId == null ? riskThresholdRepository.findAll()
                : inheritedAndOwned(riskThresholdRepository.findByPspIdIsNull(), riskThresholdRepository.findByPspId(pspId));
        RiskThreshold highRiskThreshold = visibleThresholds.stream()
                .filter(rt -> "ACTIVE".equals(rt.getStatus()))
                .filter(rt -> "HIGH".equalsIgnoreCase(rt.getRiskLevel()) || "CRITICAL".equalsIgnoreCase(rt.getRiskLevel()))
                .findFirst()
                .orElse(null);
        
        BigDecimal highRiskThresholdValue = BigDecimal.ZERO;
        if (highRiskThreshold != null) {
            highRiskThresholdValue = highRiskThreshold.getDailyLimit();
        }
        stats.put("highRiskThreshold", highRiskThresholdValue);

        // Count Active Rules (velocity rules + risk thresholds)
        List<VelocityRule> visibleVelocityRules = pspId == null ? velocityRuleRepository.findAll()
                : inheritedAndOwned(velocityRuleRepository.findByPspIdIsNull(), velocityRuleRepository.findByPspId(pspId));
        long activeVelocityRules = visibleVelocityRules.stream()
                .filter(vr -> "ACTIVE".equals(vr.getStatus()))
                .count();
        long activeRiskThresholds = visibleThresholds.stream()
                .filter(rt -> "ACTIVE".equals(rt.getStatus()))
                .count();
        stats.put("activeRulesCount", activeVelocityRules + activeRiskThresholds);

        // Calculate actual daily usage from transactions (for this PSP's merchants)
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        
        BigDecimal totalDailyUsage = transactionRepository.sumAmountByPspAndPeriod(pspId, startOfDay, endOfDay)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        stats.put("totalDailyUsage", totalDailyUsage);

        // Risk Alerts (merchants with HIGH or CRITICAL risk)
        long riskAlerts = merchants.stream()
                .filter(m -> {
                    String riskLevel = m.getRiskLevel();
                    return riskLevel != null && 
                           (riskLevel.equalsIgnoreCase("HIGH") || riskLevel.equalsIgnoreCase("CRITICAL"));
                })
                .count();
        stats.put("riskAlerts", riskAlerts);

        // Average Success Rate over the trailing 7 days, computed in-DB.
        // Definition: APPROVED transactions / total transactions * 100.
        // No fallback to a fake number — return 0.0 when there are zero rows.
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        Object[] counts = (pspId != null)
                ? transactionRepository.getApprovedAndTotalCountByPspSince(pspId, since)
                : transactionRepository.getApprovedAndTotalCountSince(since);
        long approved = 0L;
        long total = 0L;
        if (counts != null && counts.length >= 1) {
            // JPQL aggregate returns a single Object[] row; some Hibernate
            // versions wrap it in another Object[] — handle both shapes.
            Object[] row = (counts.length == 1 && counts[0] instanceof Object[])
                    ? (Object[]) counts[0]
                    : counts;
            if (row != null && row.length >= 2) {
                approved = row[0] instanceof Number ? ((Number) row[0]).longValue() : 0L;
                total    = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
            }
        }
        double avgSuccessRate = (total > 0)
                ? (approved * 100.0) / total
                : 0.0;
        // Round to 1 dp to match the previous response shape (94.2 style).
        avgSuccessRate = Math.round(avgSuccessRate * 10.0) / 10.0;
        stats.put("avgSuccessRate", avgSuccessRate);

        return stats;
    }
}

