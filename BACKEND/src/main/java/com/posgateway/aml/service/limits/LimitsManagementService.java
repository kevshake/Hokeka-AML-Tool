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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing Limits & AML Management
 */
@Service
public class LimitsManagementService {

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
        return merchantLimitRepository.findAll();
    }

    public MerchantTransactionLimit getMerchantLimit(Long merchantId) {
        return merchantLimitRepository.findByMerchant_MerchantId(merchantId)
                .orElse(null);
    }

    // Global Limits
    @Transactional
    public GlobalLimit createGlobalLimit(GlobalLimit limit, Long userId) {
        limit.setCreatedBy(userId);
        return globalLimitRepository.save(limit);
    }

    @Transactional
    public GlobalLimit updateGlobalLimit(Long id, GlobalLimit limit, Long userId) {
        GlobalLimit existing = globalLimitRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Global limit not found"));
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
        try {
            return globalLimitRepository.findAll();
        } catch (Exception e) {
            org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LimitsManagementService.class);
            log.error("Error fetching global limits: {}", e.getMessage(), e);
            // Return empty list instead of throwing to prevent complete failure
            return new java.util.ArrayList<>();
        }
    }

    @Transactional
    public void deleteGlobalLimit(Long id) {
        globalLimitRepository.deleteById(id);
    }

    // Risk Thresholds
    @Transactional
    public RiskThreshold createOrUpdateRiskThreshold(RiskThreshold threshold, Long userId) {
        User currentUser = pspIsolationService.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        RiskThreshold existing = riskThresholdRepository.findByRiskLevel(threshold.getRiskLevel())
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
            if (pspIsolationService.isPlatformAdministrator(currentUser)) {
                // Super admin creates thresholds with null pspId
                existing.setPspId(null);
            } else {
                // PSP user creates thresholds with their PSP ID
                Long pspId = pspIsolationService.getCurrentUserPspId();
                existing.setPspId(pspId);
            }
        }

        // Update merchant count
        long merchantCount = merchantRepository.findAll().stream()
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
        return riskThresholdRepository.findAll();
    }

    // Velocity Rules
    @Transactional
    public VelocityRule createVelocityRule(VelocityRule rule, Long userId) {
        User currentUser = pspIsolationService.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        rule.setCreatedBy(userId);
        if (pspIsolationService.isPlatformAdministrator(currentUser)) {
            // Super admin creates rules with null pspId
            rule.setPspId(null);
        } else {
            // PSP user creates rules with their PSP ID
            Long pspId = pspIsolationService.getCurrentUserPspId();
            rule.setPspId(pspId);
        }
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
        return velocityRuleRepository.findAll();
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
        CountryComplianceRule existing = countryComplianceRepository.findByCountryCode(rule.getCountryCode())
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
        }

        return countryComplianceRepository.save(existing);
    }

    public List<CountryComplianceRule> getAllCountryComplianceRules() {
        return countryComplianceRepository.findAll();
    }

    // Dashboard Statistics
    public Map<String, Object> getDashboardStats(Long pspId) {
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
            merchantLimits = merchantLimitRepository.findAll().stream()
                    .filter(limit -> limit.getMerchant() != null && 
                            limit.getMerchant().getPsp() != null &&
                            limit.getMerchant().getPsp().getPspId().equals(pspId))
                    .collect(Collectors.toList());
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
        RiskThreshold highRiskThreshold = riskThresholdRepository.findAll().stream()
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
        long activeVelocityRules = velocityRuleRepository.findAll().stream()
                .filter(vr -> "ACTIVE".equals(vr.getStatus()))
                .count();
        long activeRiskThresholds = riskThresholdRepository.findAll().stream()
                .filter(rt -> "ACTIVE".equals(rt.getStatus()))
                .count();
        stats.put("activeRulesCount", activeVelocityRules + activeRiskThresholds);

        // Calculate actual daily usage from transactions (for this PSP's merchants)
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
        
        BigDecimal totalDailyUsage = BigDecimal.ZERO;
        if (pspId != null && !merchants.isEmpty()) {
            // Get merchant IDs as strings (since TransactionEntity uses String merchantId)
            List<String> merchantIdStrings = merchants.stream()
                    .map(m -> String.valueOf(m.getMerchantId()))
                    .collect(Collectors.toList());
            
            // Query transactions for today
            List<com.posgateway.aml.entity.TransactionEntity> todayTransactions = 
                transactionRepository.findAll().stream()
                    .filter(tx -> tx.getMerchantId() != null &&
                            merchantIdStrings.contains(tx.getMerchantId()) &&
                            tx.getTxnTs() != null &&
                            tx.getTxnTs().isAfter(startOfDay) &&
                            tx.getTxnTs().isBefore(endOfDay))
                    .collect(Collectors.toList());
            
            // Sum transaction amounts
            for (com.posgateway.aml.entity.TransactionEntity tx : todayTransactions) {
                if (tx.getAmountCents() != null) {
                    totalDailyUsage = totalDailyUsage.add(
                        BigDecimal.valueOf(tx.getAmountCents()).divide(BigDecimal.valueOf(100))
                    );
                }
            }
        } else {
            // For global admin, sum from global limits
            totalDailyUsage = globalLimitRepository.findAll().stream()
                    .filter(g -> "VOLUME".equals(g.getLimitType()) && "DAY".equals(g.getPeriod()))
                    .map(GlobalLimit::getCurrentUsage)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
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

        // Average Success Rate (mock for now)
        stats.put("avgSuccessRate", 94.2);

        return stats;
    }
}

