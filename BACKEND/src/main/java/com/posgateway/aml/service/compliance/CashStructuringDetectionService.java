package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared evidence-backed detector for Kenya cash reporting and structuring.
 *
 * <p>All amounts are converted with an approved regulatory FX rate before they
 * are compared with the USD 15,000 reporting threshold. Non-cash transactions
 * are never classified as CTR or cash structuring candidates.
 */
@Service
public class CashStructuringDetectionService {

    private static final Duration WINDOW = Duration.ofHours(24);

    private final TransactionRepository transactionRepository;
    private final RegulatoryExchangeRateService exchangeRateService;
    private final BigDecimal ctrThresholdUsd;
    private final BigDecimal structuringFloorRatio;
    private final int minimumTransactions;

    public CashStructuringDetectionService(
            TransactionRepository transactionRepository,
            RegulatoryExchangeRateService exchangeRateService,
            @Value("${compliance.kenya.ctr.threshold-usd:15000}") BigDecimal ctrThresholdUsd,
            @Value("${compliance.kenya.structuring.floor-ratio:0.80}") BigDecimal structuringFloorRatio,
            @Value("${compliance.kenya.structuring.minimum-transactions:2}") int minimumTransactions) {
        this.transactionRepository = transactionRepository;
        this.exchangeRateService = exchangeRateService;
        this.ctrThresholdUsd = ctrThresholdUsd;
        this.structuringFloorRatio = structuringFloorRatio;
        this.minimumTransactions = Math.max(2, minimumTransactions);
    }

    @Transactional(readOnly = true)
    public Assessment assess(TransactionEntity transaction) {
        Map<String, Object> evidence = baseEvidence(transaction);
        if (transaction == null || !transaction.isCashTransaction()) {
            evidence.put("ctrEvaluationStatus", "NOT_CASH");
            return new Assessment(false, false, "NOT_CASH", 0, null, evidence);
        }

        ConvertedTransaction current = convert(transaction);
        if (!current.available()) {
            evidence.put("ctrEvaluationStatus", "FX_UNAVAILABLE");
            evidence.put("fxUnavailableReason", current.unavailableReason());
            return new Assessment(false, false, "FX_UNAVAILABLE", 0, null, evidence);
        }
        addConversionEvidence(evidence, current);

        if (current.amountUsd().compareTo(ctrThresholdUsd) >= 0) {
            evidence.put("ctrEvaluationStatus", "REPORTABLE");
            return new Assessment(true, false, "REPORTABLE", 1, current.amountUsd(), evidence);
        }

        BigDecimal floor = structuringFloor();
        evidence.put("structuringFloorUsd", floor);
        if (current.amountUsd().compareTo(floor) < 0) {
            evidence.put("ctrEvaluationStatus", "BELOW_STRUCTURING_BAND");
            return new Assessment(false, false, "BELOW_STRUCTURING_BAND", 0, current.amountUsd(), evidence);
        }

        List<TransactionEntity> recent = loadWindow(transaction);
        List<ConvertedTransaction> candidates = convertStructuringCandidates(recent);
        int count = candidates.size();
        BigDecimal total = candidates.stream()
                .map(ConvertedTransaction::amountUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        evidence.put("ctrEvaluationStatus",
                count >= minimumTransactions ? "STRUCTURING_SUSPECTED" : "BELOW_THRESHOLD");
        evidence.put("structuringTransactionCount24h", count);
        evidence.put("structuringTotalUsd24h", total);
        evidence.put("structuringTransactionIds", candidates.stream()
                .map(ConvertedTransaction::transactionId)
                .filter(id -> id != null)
                .toList());

        return new Assessment(
                false,
                count >= minimumTransactions,
                count >= minimumTransactions ? "STRUCTURING_SUSPECTED" : "BELOW_THRESHOLD",
                count,
                total,
                evidence);
    }

    /**
     * Finds every account-level 24-hour occurrence in the supplied population.
     * Each occurrence contains the exact transactions and FX evidence used.
     */
    public List<Occurrence> detectOccurrences(List<TransactionEntity> transactions) {
        if (transactions == null || transactions.isEmpty()) return List.of();

        Map<String, List<ConvertedTransaction>> byAccount = new LinkedHashMap<>();
        List<Occurrence> unavailable = new ArrayList<>();
        for (TransactionEntity transaction : transactions) {
            if (transaction == null || !transaction.isCashTransaction()) continue;
            String account = accountKey(transaction);
            if (account == null) continue;

            ConvertedTransaction converted = convert(transaction);
            if (!converted.available()) {
                unavailable.add(Occurrence.unavailable(
                        account, transaction, converted.unavailableReason(), ctrThresholdUsd, structuringFloor()));
                continue;
            }
            if (converted.amountUsd().compareTo(structuringFloor()) >= 0
                    && converted.amountUsd().compareTo(ctrThresholdUsd) < 0) {
                byAccount.computeIfAbsent(account, ignored -> new ArrayList<>()).add(converted);
            }
        }

        List<Occurrence> detections = new ArrayList<>(unavailable);
        for (Map.Entry<String, List<ConvertedTransaction>> entry : byAccount.entrySet()) {
            List<ConvertedTransaction> candidates = entry.getValue().stream()
                    .sorted(Comparator.comparing(ConvertedTransaction::transactionTime))
                    .toList();
            int left = 0;
            for (int right = 0; right < candidates.size(); right++) {
                while (left < right && Duration.between(
                        candidates.get(left).transactionTime(),
                        candidates.get(right).transactionTime()).compareTo(WINDOW) > 0) {
                    left++;
                }
                if (right - left + 1 >= minimumTransactions) {
                    List<ConvertedTransaction> window = candidates.subList(left, right + 1);
                    detections.add(Occurrence.detected(
                            entry.getKey(), window, ctrThresholdUsd, structuringFloor()));
                }
            }
        }
        return detections;
    }

    private List<TransactionEntity> loadWindow(TransactionEntity current) {
        LocalDateTime at = transactionTime(current);
        String account = accountKey(current);
        if (account == null) return List.of(current);

        List<TransactionEntity> transactions = new ArrayList<>(
                transactionRepository.findByPanHashAndTxnTsBetweenOrderByTxnTsAsc(
                        account, at.minus(WINDOW), at));
        boolean containsCurrent = transactions.stream().anyMatch(existing ->
                current.getTxnId() != null && current.getTxnId().equals(existing.getTxnId()));
        if (!containsCurrent) transactions.add(current);
        return transactions;
    }

    private List<ConvertedTransaction> convertStructuringCandidates(List<TransactionEntity> transactions) {
        BigDecimal floor = structuringFloor();
        return transactions.stream()
                .filter(TransactionEntity::isCashTransaction)
                .map(this::convert)
                .filter(ConvertedTransaction::available)
                .filter(item -> item.amountUsd().compareTo(floor) >= 0)
                .filter(item -> item.amountUsd().compareTo(ctrThresholdUsd) < 0)
                .sorted(Comparator.comparing(ConvertedTransaction::transactionTime))
                .toList();
    }

    private ConvertedTransaction convert(TransactionEntity transaction) {
        BigDecimal amount = transaction != null && transaction.getAmountCents() != null
                ? BigDecimal.valueOf(transaction.getAmountCents()).movePointLeft(2)
                : null;
        LocalDateTime at = transactionTime(transaction);
        RegulatoryExchangeRateService.ConversionResult result =
                exchangeRateService.convertToUsd(amount, transaction != null ? transaction.getCurrency() : null, at);
        if (!result.available()) {
            return ConvertedTransaction.unavailable(transaction, at, result.unavailableReason());
        }
        return ConvertedTransaction.available(transaction, at, result);
    }

    private Map<String, Object> baseEvidence(TransactionEntity transaction) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("cashTransaction", transaction != null && transaction.isCashTransaction());
        evidence.put("ctrThresholdUsd", ctrThresholdUsd);
        evidence.put("structuringFloorRatio", structuringFloorRatio);
        evidence.put("structuringWindowHours", WINDOW.toHours());
        evidence.put("minimumStructuringTransactions", minimumTransactions);
        if (transaction != null) {
            evidence.put("transactionId", transaction.getTxnId());
            evidence.put("accountId", accountKey(transaction));
            evidence.put("currency", transaction.getCurrency());
            evidence.put("transactionTime", transactionTime(transaction));
        }
        return evidence;
    }

    private static void addConversionEvidence(Map<String, Object> evidence, ConvertedTransaction converted) {
        evidence.put("amountUsd", converted.amountUsd());
        evidence.put("rateToUsd", converted.rateToUsd());
        evidence.put("rateSource", converted.rateSource());
        evidence.put("rateEffectiveAt", converted.rateEffectiveAt());
    }

    private BigDecimal structuringFloor() {
        return ctrThresholdUsd.multiply(structuringFloorRatio);
    }

    private static LocalDateTime transactionTime(TransactionEntity transaction) {
        return transaction != null && transaction.getTxnTs() != null
                ? transaction.getTxnTs() : LocalDateTime.now();
    }

    private static String accountKey(TransactionEntity transaction) {
        if (transaction == null || transaction.getPanHash() == null
                || transaction.getPanHash().isBlank()) return null;
        return transaction.getPanHash();
    }

    public record Assessment(
            boolean ctrRequired,
            boolean structuringSuspected,
            String status,
            int transactionCount24h,
            BigDecimal totalUsd24h,
            Map<String, Object> evidence) {
    }

    public record Occurrence(
            String accountId,
            LocalDateTime windowStart,
            LocalDateTime windowEnd,
            String status,
            int transactionCount,
            BigDecimal totalUsd,
            BigDecimal thresholdUsd,
            BigDecimal structuringFloorUsd,
            List<Long> transactionIds,
            Set<String> currencies,
            List<Map<String, Object>> conversionEvidence,
            String unavailableReason) {

        static Occurrence detected(String account, List<ConvertedTransaction> window,
                                   BigDecimal threshold, BigDecimal floor) {
            BigDecimal total = window.stream().map(ConvertedTransaction::amountUsd)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Set<String> currencies = new LinkedHashSet<>();
            List<Map<String, Object>> evidence = new ArrayList<>();
            for (ConvertedTransaction item : window) {
                if (item.currency() != null) currencies.add(item.currency());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("transactionId", item.transactionId());
                row.put("transactionTime", item.transactionTime());
                row.put("currency", item.currency());
                row.put("amountUsd", item.amountUsd());
                row.put("rateToUsd", item.rateToUsd());
                row.put("rateSource", item.rateSource());
                row.put("rateEffectiveAt", item.rateEffectiveAt());
                evidence.add(row);
            }
            return new Occurrence(
                    account,
                    window.get(0).transactionTime(),
                    window.get(window.size() - 1).transactionTime(),
                    "STRUCTURING_SUSPECTED",
                    window.size(),
                    total,
                    threshold,
                    floor,
                    window.stream().map(ConvertedTransaction::transactionId).filter(id -> id != null).toList(),
                    Set.copyOf(currencies),
                    List.copyOf(evidence),
                    null);
        }

        static Occurrence unavailable(String account, TransactionEntity transaction, String reason,
                                      BigDecimal threshold, BigDecimal floor) {
            LocalDateTime at = transactionTime(transaction);
            return new Occurrence(
                    account, at, at, "FX_UNAVAILABLE", 1, null, threshold, floor,
                    transaction.getTxnId() == null ? List.of() : List.of(transaction.getTxnId()),
                    transaction.getCurrency() == null ? Set.of() : Set.of(transaction.getCurrency()),
                    List.of(),
                    reason);
        }
    }

    private record ConvertedTransaction(
            Long transactionId,
            LocalDateTime transactionTime,
            String currency,
            BigDecimal amountUsd,
            BigDecimal rateToUsd,
            String rateSource,
            LocalDateTime rateEffectiveAt,
            String unavailableReason) {

        static ConvertedTransaction available(
                TransactionEntity transaction,
                LocalDateTime at,
                RegulatoryExchangeRateService.ConversionResult result) {
            return new ConvertedTransaction(
                    transaction != null ? transaction.getTxnId() : null,
                    at,
                    transaction != null ? transaction.getCurrency() : null,
                    result.amountUsd(),
                    result.rateToUsd(),
                    result.source(),
                    result.effectiveAt(),
                    null);
        }

        static ConvertedTransaction unavailable(
                TransactionEntity transaction, LocalDateTime at, String reason) {
            return new ConvertedTransaction(
                    transaction != null ? transaction.getTxnId() : null,
                    at,
                    transaction != null ? transaction.getCurrency() : null,
                    null, null, null, null, reason);
        }

        boolean available() {
            return unavailableReason == null;
        }
    }
}
