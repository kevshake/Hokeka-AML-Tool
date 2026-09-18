package com.posgateway.aml.service.alert;

import com.posgateway.aml.entity.alert.RuleAbTest;
import com.posgateway.aml.entity.alert.RuleAbTestResult;
import com.posgateway.aml.repository.alert.RuleAbTestRepository;
import com.posgateway.aml.repository.alert.RuleAbTestResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * A/B Testing for Rules Service
 * Framework for testing rule changes
 */
@Service
public class RuleAbTestingService {

    private static final Logger logger = LoggerFactory.getLogger(RuleAbTestingService.class);

    private final RuleAbTestRepository testRepository;
    private final RuleAbTestResultRepository resultRepository;

    @Autowired
    public RuleAbTestingService(
            RuleAbTestRepository testRepository,
            RuleAbTestResultRepository resultRepository) {
        this.testRepository = testRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * Create A/B test for a rule
     */
    @Transactional
    public RuleAbTest createAbTest(String ruleName, String variantA, String variantB,
                                   int trafficSplitPercent, LocalDateTime endDate) {
        RuleAbTest test = new RuleAbTest();
        test.setRuleName(ruleName);
        test.setVariantA(variantA);
        test.setVariantB(variantB);
        test.setTrafficSplitPercent(trafficSplitPercent);
        test.setStatus("ACTIVE");
        test.setStartDate(LocalDateTime.now());
        test.setEndDate(endDate);
        test.setCreatedAt(LocalDateTime.now());

        logger.info("Created A/B test for rule {}: {}% traffic to variant A", ruleName, trafficSplitPercent);
        return testRepository.save(test);
    }

    /**
     * Record test result
     */
    @Transactional
    public RuleAbTestResult recordResult(Long testId, String variant, boolean isTruePositive) {
        RuleAbTest test = testRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("A/B test not found: " + testId));

        RuleAbTestResult result = new RuleAbTestResult();
        result.setTest(test);
        result.setVariant(variant);
        result.setTruePositive(isTruePositive);
        result.setRecordedAt(LocalDateTime.now());

        return resultRepository.save(result);
    }

    /**
     * Get test results summary
     */
    public Map<String, Object> getTestResults(Long testId) {
        RuleAbTest test = testRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("A/B test not found: " + testId));

        List<RuleAbTestResult> results = resultRepository.findByTestId(testId);

        long variantACount = results.stream()
                .filter(r -> "A".equals(r.getVariant()))
                .count();
        long variantBCount = results.stream()
                .filter(r -> "B".equals(r.getVariant()))
                .count();

        long variantATruePositives = results.stream()
                .filter(r -> "A".equals(r.getVariant()) && r.isTruePositive())
                .count();
        long variantBTruePositives = results.stream()
                .filter(r -> "B".equals(r.getVariant()) && r.isTruePositive())
                .count();

        Map<String, Object> summary = new HashMap<>();
        summary.put("testId", testId);
        summary.put("ruleName", test.getRuleName());
        summary.put("variantACount", variantACount);
        summary.put("variantBCount", variantBCount);
        summary.put("variantATruePositiveRate", variantACount > 0 ? 
                variantATruePositives / (double) variantACount : 0.0);
        summary.put("variantBTruePositiveRate", variantBCount > 0 ? 
                variantBTruePositives / (double) variantBCount : 0.0);

        return summary;
    }

    /**
     * End A/B test and determine winner
     */
    @Transactional
    public String endTestAndDetermineWinner(Long testId) {
        RuleAbTest test = testRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("A/B test not found: " + testId));

        Map<String, Object> results = getTestResults(testId);
        double variantARate = (Double) results.get("variantATruePositiveRate");
        double variantBRate = (Double) results.get("variantBTruePositiveRate");

        test.setStatus("COMPLETED");
        test.setEndDate(LocalDateTime.now());
        testRepository.save(test);

        String winner = variantARate > variantBRate ? "A" : "B";
        logger.info("A/B test {} completed. Winner: Variant {} (A: {}%, B: {}%)",
                testId, winner, variantARate * 100, variantBRate * 100);

        return winner;
    }
}

