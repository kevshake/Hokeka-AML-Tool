package com.posgateway.aml.service.mobilemoney;

import com.posgateway.aml.dto.mobilemoney.MobileMoneyDtos.IngestMobileMoneyRequest;
import com.posgateway.aml.entity.mobilemoney.MobileMoneyEventType;
import com.posgateway.aml.entity.mobilemoney.MobileMoneyTransactionContext;
import com.posgateway.aml.entity.multiasset.FinancialCrimeSignalType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MobileMoneyRiskEngine {
    private final BigDecimal highValueUsd;
    private final int sharedDeviceWallets;
    private final int sharedSimWallets;
    private final int rapidMinutes;
    private final int dormantDays;
    private final double impossibleSpeedKph;
    private final int nightStartHour;
    private final int nightEndHour;

    public MobileMoneyRiskEngine(
            @Value("${mobilemoney.high-value-usd:1000}") BigDecimal highValueUsd,
            @Value("${mobilemoney.shared-device-wallets:3}") int sharedDeviceWallets,
            @Value("${mobilemoney.shared-sim-wallets:2}") int sharedSimWallets,
            @Value("${mobilemoney.rapid-flow-minutes:30}") int rapidMinutes,
            @Value("${mobilemoney.dormant-days:30}") int dormantDays,
            @Value("${mobilemoney.impossible-speed-kph:500}") double impossibleSpeedKph,
            @Value("${mobilemoney.night-start-hour:0}") int nightStartHour,
            @Value("${mobilemoney.night-end-hour:5}") int nightEndHour) {
        this.highValueUsd = highValueUsd;
        this.sharedDeviceWallets = sharedDeviceWallets;
        this.sharedSimWallets = sharedSimWallets;
        this.rapidMinutes = rapidMinutes;
        this.dormantDays = dormantDays;
        this.impossibleSpeedKph = impossibleSpeedKph;
        this.nightStartHour = nightStartHour;
        this.nightEndHour = nightEndHour;
    }

    public Assessment assess(IngestMobileMoneyRequest request, List<MobileMoneyTransactionContext> history) {
        LocalDateTime occurredAt = timestamp(request);
        List<SignalDraft> signals = new ArrayList<>();
        checkSharedControl(request, history, signals);
        checkRapidFlows(request, history, occurredAt, signals);
        checkDeviceAndSimChanges(request, history, occurredAt, signals);
        checkAgentRisk(request, history, occurredAt, signals);
        checkLocationAndTime(request, history, occurredAt, signals);
        checkMerchantRisk(request, history, occurredAt, signals);
        checkCrossBorderFragmentation(request, history, occurredAt, signals);
        checkDormantReactivation(request, occurredAt, signals);
        int score = Math.min(100, signals.stream().mapToInt(SignalDraft::scoreImpact).sum());
        return new Assessment(score, List.copyOf(signals));
    }

    private void checkSharedControl(IngestMobileMoneyRequest request,
            List<MobileMoneyTransactionContext> history, List<SignalDraft> signals) {
        if (present(request.deviceFingerprint())) {
            Set<String> wallets = matchingWallets(history, request.deviceFingerprint(), true);
            wallets.add(request.walletReference());
            if (wallets.size() >= sharedDeviceWallets) {
                add(signals, "MOBILE_SHARED_DEVICE_MULTIPLE_WALLETS", FinancialCrimeSignalType.FRAUD, 35,
                        "One device controls multiple mobile-money wallets.",
                        evidence("walletCount", wallets.size(), "wallets", sorted(wallets),
                                "configuredThreshold", sharedDeviceWallets));
            }
            if (request.eventType() == MobileMoneyEventType.CASH_IN && request.fiatEquivalentUsd() != null) {
                long structuredTopUps = history.stream()
                        .filter(item -> same(item.getDeviceFingerprint(), request.deviceFingerprint()))
                        .filter(item -> item.getEventType() == MobileMoneyEventType.CASH_IN)
                        .map(MobileMoneyTransactionContext::getTransaction)
                        .filter(item -> item.getFiatEquivalentUsd() != null)
                        .filter(item -> item.getFiatEquivalentUsd().compareTo(highValueUsd) < 0)
                        .count() + (request.fiatEquivalentUsd().compareTo(highValueUsd) < 0 ? 1 : 0);
                if (wallets.size() >= 2 && structuredTopUps >= 5) {
                    add(signals, "MOBILE_MULTI_WALLET_STRUCTURING", FinancialCrimeSignalType.AML, 40,
                            "Repeated sub-threshold cash-ins were distributed across device-linked wallets.",
                            evidence("walletCount", wallets.size(), "cashInCount", structuredTopUps));
                }
            }
        }
        if (present(request.simIdentifierHash())) {
            Set<String> wallets = history.stream()
                    .filter(item -> same(item.getSimIdentifierHash(), request.simIdentifierHash()))
                    .map(MobileMoneyTransactionContext::getWalletReference)
                    .filter(Objects::nonNull).collect(java.util.stream.Collectors.toCollection(HashSet::new));
            wallets.add(request.walletReference());
            if (wallets.size() >= sharedSimWallets) {
                add(signals, "MOBILE_SHARED_SIM_MULTIPLE_WALLETS", FinancialCrimeSignalType.FRAUD, 30,
                        "One SIM identifier is linked to multiple wallets.",
                        evidence("walletCount", wallets.size(), "wallets", sorted(wallets),
                                "configuredThreshold", sharedSimWallets));
            }
        }
    }

    private void checkRapidFlows(IngestMobileMoneyRequest request, List<MobileMoneyTransactionContext> history,
            LocalDateTime occurredAt, List<SignalDraft> signals) {
        LocalDateTime rapidCutoff = occurredAt.minusMinutes(rapidMinutes);
        List<MobileMoneyTransactionContext> walletHistory = history.stream()
                .filter(item -> same(item.getWalletReference(), request.walletReference()))
                .filter(item -> !item.getExecutedAt().isBefore(rapidCutoff))
                .toList();
        if (request.eventType() == MobileMoneyEventType.CASH_OUT) {
            Optional<MobileMoneyTransactionContext> cashIn = walletHistory.stream()
                    .filter(item -> item.getEventType() == MobileMoneyEventType.CASH_IN)
                    .filter(item -> item.getTransaction().getAmount()
                            .compareTo(request.amount().multiply(BigDecimal.valueOf(0.8))) >= 0)
                    .findFirst();
            cashIn.ifPresent(item -> add(signals, "MOBILE_RAPID_CASH_IN_CASH_OUT",
                    FinancialCrimeSignalType.AML, 40,
                    "Cash was withdrawn shortly after a materially similar cash-in.",
                    evidence("cashInTransactionId", item.getTransaction().getId(),
                            "cashInAmount", item.getTransaction().getAmount(), "windowMinutes", rapidMinutes)));
        }

        if (request.eventType() == MobileMoneyEventType.P2P_TRANSFER) {
            Set<String> counterparties = walletHistory.stream()
                    .filter(item -> item.getEventType() == MobileMoneyEventType.P2P_TRANSFER)
                    .map(MobileMoneyTransactionContext::getCounterpartyWalletReference)
                    .filter(MobileMoneyRiskEngine::present)
                    .collect(java.util.stream.Collectors.toCollection(HashSet::new));
            if (present(request.counterpartyWalletReference())) counterparties.add(request.counterpartyWalletReference());
            if (walletHistory.size() + 1 >= 4 && counterparties.size() >= 3) {
                add(signals, "MOBILE_RAPID_WALLET_CIRCULATION", FinancialCrimeSignalType.AML, 35,
                        "A wallet circulated funds across several counterparties in a short period.",
                        evidence("counterpartyCount", counterparties.size(), "windowMinutes", rapidMinutes));
            }
        }

        if (present(request.counterpartyWalletReference())) {
            long repeated = history.stream()
                    .filter(item -> !item.getExecutedAt().isBefore(occurredAt.minusHours(24)))
                    .filter(item -> same(item.getWalletReference(), request.walletReference()))
                    .filter(item -> same(item.getCounterpartyWalletReference(), request.counterpartyWalletReference()))
                    .count() + 1;
            if (repeated >= 5) {
                add(signals, "MOBILE_REPEATED_WALLET_CLUSTER", FinancialCrimeSignalType.AML, 25,
                        "The same wallet pair transacted repeatedly within 24 hours.",
                        evidence("transactionCount", repeated, "counterpartyWallet", request.counterpartyWalletReference()));
            }
        }
    }

    private void checkDeviceAndSimChanges(IngestMobileMoneyRequest request,
            List<MobileMoneyTransactionContext> history, LocalDateTime occurredAt, List<SignalDraft> signals) {
        boolean highValue = request.fiatEquivalentUsd() != null
                && request.fiatEquivalentUsd().compareTo(highValueUsd) >= 0;
        if (highValue && request.simChangedAt() != null
                && !request.simChangedAt().isAfter(occurredAt)
                && !request.simChangedAt().isBefore(occurredAt.minusHours(24))) {
            add(signals, "MOBILE_SIM_SWAP_HIGH_VALUE", FinancialCrimeSignalType.FRAUD, 55,
                    "A high-value transaction followed a recent SIM replacement.",
                    evidence("simChangedAt", request.simChangedAt(), "fiatEquivalentUsd", request.fiatEquivalentUsd()));
        }
        if (highValue && present(request.deviceFingerprint())) {
            boolean hasWalletHistory = history.stream().anyMatch(item -> same(item.getWalletReference(), request.walletReference()));
            boolean knownDevice = history.stream()
                    .filter(item -> same(item.getWalletReference(), request.walletReference()))
                    .anyMatch(item -> same(item.getDeviceFingerprint(), request.deviceFingerprint()));
            if (hasWalletHistory && !knownDevice) {
                add(signals, "MOBILE_NEW_DEVICE_HIGH_VALUE", FinancialCrimeSignalType.FRAUD, 35,
                        "A high-value transaction originated from a device not previously observed for the wallet.",
                        evidence("fiatEquivalentUsd", request.fiatEquivalentUsd()));
            }
        }
    }

    private void checkAgentRisk(IngestMobileMoneyRequest request, List<MobileMoneyTransactionContext> history,
            LocalDateTime occurredAt, List<SignalDraft> signals) {
        if (present(request.deviceFingerprint()) && present(request.agentDeviceFingerprint())
                && same(request.deviceFingerprint(), request.agentDeviceFingerprint())) {
            add(signals, "MOBILE_CUSTOMER_AGENT_SHARED_DEVICE", FinancialCrimeSignalType.FRAUD, 50,
                    "Customer and agent activity used the same device.", Map.of());
        }
        if (present(request.deviceFingerprint())) {
            boolean previouslyAgentDevice = history.stream()
                    .anyMatch(item -> same(item.getAgentDeviceFingerprint(), request.deviceFingerprint()));
            if (previouslyAgentDevice) {
                add(signals, "MOBILE_DEVICE_AGENT_ROLE_OVERLAP", FinancialCrimeSignalType.FRAUD, 40,
                        "A device previously used by an agent is now operating a customer wallet.", Map.of());
            }
        }
        if (present(request.agentReference())) {
            long agentWalletEvents = history.stream()
                    .filter(item -> !item.getExecutedAt().isBefore(occurredAt.minusHours(1)))
                    .filter(item -> same(item.getAgentReference(), request.agentReference()))
                    .filter(item -> same(item.getWalletReference(), request.walletReference()))
                    .count() + 1;
            if (agentWalletEvents >= 5) {
                add(signals, "MOBILE_AGENT_WALLET_CONCENTRATION", FinancialCrimeSignalType.AML, 30,
                        "An agent repeatedly serviced the same wallet in a one-hour window.",
                        evidence("eventCount", agentWalletEvents, "agentReference", request.agentReference()));
            }
        }
        if (request.agentFloatBefore() != null && request.agentFloatAfter() != null) {
            BigDecimal change = request.agentFloatAfter().subtract(request.agentFloatBefore());
            boolean signMismatch = request.eventType() == MobileMoneyEventType.CASH_IN && change.signum() > 0
                    || request.eventType() == MobileMoneyEventType.CASH_OUT && change.signum() < 0;
            boolean magnitudeMismatch = change.abs().compareTo(request.amount().multiply(BigDecimal.valueOf(1.25))) > 0;
            if (signMismatch || magnitudeMismatch) {
                add(signals, "MOBILE_ABNORMAL_AGENT_FLOAT", FinancialCrimeSignalType.AML, 35,
                        "Observed agent float movement is inconsistent with the transaction event.",
                        evidence("floatBefore", request.agentFloatBefore(), "floatAfter", request.agentFloatAfter(),
                                "floatChange", change, "eventType", request.eventType()));
            }
        }
    }

    private void checkLocationAndTime(IngestMobileMoneyRequest request,
            List<MobileMoneyTransactionContext> history, LocalDateTime occurredAt, List<SignalDraft> signals) {
        int hour = occurredAt.getHour();
        // Support wrap-around windows (e.g. 22:00–05:00): when start > end the night
        // spans midnight, so the hour matches if it is at/after start OR before end (W49-9).
        boolean inNightWindow = (nightStartHour <= nightEndHour)
                ? (hour >= nightStartHour && hour < nightEndHour)
                : (hour >= nightStartHour || hour < nightEndHour);
        if (inNightWindow) {
            add(signals, "MOBILE_UNUSUAL_NIGHT_ACTIVITY", FinancialCrimeSignalType.FRAUD, 15,
                    "Mobile-money activity occurred during the configured night-risk window.",
                    evidence("hour", hour, "window", nightStartHour + "-" + nightEndHour));
        }
        if (request.latitude() == null || request.longitude() == null) return;
        history.stream()
                .filter(item -> same(item.getWalletReference(), request.walletReference()))
                .filter(item -> item.getLatitude() != null && item.getLongitude() != null)
                .filter(item -> item.getExecutedAt().isBefore(occurredAt))
                .max(Comparator.comparing(MobileMoneyTransactionContext::getExecutedAt))
                .ifPresent(previous -> {
                    long seconds = Duration.between(previous.getExecutedAt(), occurredAt).getSeconds();
                    if (seconds < 60) return;
                    double distance = distanceKm(previous.getLatitude(), previous.getLongitude(),
                            request.latitude(), request.longitude());
                    double speed = distance / (seconds / 3600.0);
                    if (speed > impossibleSpeedKph) {
                        add(signals, "MOBILE_IMPOSSIBLE_GEO_VELOCITY", FinancialCrimeSignalType.FRAUD, 45,
                                "Consecutive wallet events imply geographically impossible travel.",
                                evidence("previousTransactionId", previous.getTransaction().getId(),
                                        "distanceKm", rounded(distance), "impliedSpeedKph", rounded(speed),
                                        "configuredMaximumKph", impossibleSpeedKph));
                    }
                });
    }

    private void checkMerchantRisk(IngestMobileMoneyRequest request,
            List<MobileMoneyTransactionContext> history, LocalDateTime occurredAt, List<SignalDraft> signals) {
        if (!present(request.merchantReference())) return;
        if (request.eventType() == MobileMoneyEventType.REFUND) {
            long refunds = history.stream()
                    .filter(item -> !item.getExecutedAt().isBefore(occurredAt.minusHours(24)))
                    .filter(item -> same(item.getMerchantReference(), request.merchantReference()))
                    .filter(item -> item.getEventType() == MobileMoneyEventType.REFUND)
                    .count() + 1;
            if (refunds >= 3) {
                add(signals, "MOBILE_MERCHANT_REFUND_ABUSE", FinancialCrimeSignalType.FRAUD, 35,
                        "A merchant generated repeated refunds within 24 hours.",
                        evidence("refundCount", refunds, "merchantReference", request.merchantReference()));
            }
        }
        if (request.eventType() == MobileMoneyEventType.CASH_OUT
                || request.eventType() == MobileMoneyEventType.P2P_TRANSFER) {
            Optional<MobileMoneyTransactionContext> recentFunding = history.stream()
                    .filter(item -> !item.getExecutedAt().isBefore(occurredAt.minusMinutes(rapidMinutes)))
                    .filter(item -> same(item.getMerchantReference(), request.merchantReference()))
                    .filter(item -> item.getEventType() == MobileMoneyEventType.CASH_IN
                            || item.getEventType() == MobileMoneyEventType.MERCHANT_PAYMENT)
                    .findFirst();
            recentFunding.ifPresent(item -> add(signals, "MOBILE_MERCHANT_PASS_THROUGH",
                    FinancialCrimeSignalType.AML, 40,
                    "Merchant-linked value was rapidly transferred or cashed out.",
                    evidence("fundingTransactionId", item.getTransaction().getId(), "windowMinutes", rapidMinutes)));
        }
    }

    private void checkCrossBorderFragmentation(IngestMobileMoneyRequest request,
            List<MobileMoneyTransactionContext> history, LocalDateTime occurredAt, List<SignalDraft> signals) {
        if (request.eventType() != MobileMoneyEventType.P2P_TRANSFER) return;
        List<MobileMoneyTransactionContext> recent = history.stream()
                .filter(item -> same(item.getWalletReference(), request.walletReference()))
                .filter(item -> !item.getExecutedAt().isBefore(occurredAt.minusHours(1)))
                .toList();
        boolean crossBorderReceipt = recent.stream().anyMatch(MobileMoneyTransactionContext::isCrossBorder);
        Set<String> recipients = recent.stream()
                .filter(item -> item.getEventType() == MobileMoneyEventType.P2P_TRANSFER)
                .map(MobileMoneyTransactionContext::getCounterpartyWalletReference)
                .filter(MobileMoneyRiskEngine::present)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        if (present(request.counterpartyWalletReference())) recipients.add(request.counterpartyWalletReference());
        if (crossBorderReceipt && recipients.size() >= 3) {
            add(signals, "MOBILE_CROSS_BORDER_FRAGMENTATION", FinancialCrimeSignalType.AML, 45,
                    "A recent cross-border receipt was fragmented across multiple domestic wallets.",
                    evidence("recipientCount", recipients.size(), "windowHours", 1));
        }
    }

    private void checkDormantReactivation(IngestMobileMoneyRequest request,
            LocalDateTime occurredAt, List<SignalDraft> signals) {
        if (request.walletPreviousActivityAt() != null
                && !request.walletPreviousActivityAt().isAfter(occurredAt)
                && request.walletPreviousActivityAt().isBefore(occurredAt.minusDays(dormantDays))) {
            add(signals, "MOBILE_DORMANT_WALLET_REACTIVATION", FinancialCrimeSignalType.FRAUD, 30,
                    "A dormant wallet resumed activity after the configured inactivity period.",
                    evidence("previousActivityAt", request.walletPreviousActivityAt(), "dormantDays", dormantDays));
        }
    }

    private Set<String> matchingWallets(List<MobileMoneyTransactionContext> history, String identifier,
            boolean device) {
        return history.stream()
                .filter(item -> same(device ? item.getDeviceFingerprint() : item.getSimIdentifierHash(), identifier))
                .map(MobileMoneyTransactionContext::getWalletReference)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    private static double distanceKm(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double earthRadius = 6371.0088;
        double firstLat = Math.toRadians(lat1.doubleValue());
        double secondLat = Math.toRadians(lat2.doubleValue());
        double latDelta = secondLat - firstLat;
        double lonDelta = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(latDelta / 2) * Math.sin(latDelta / 2)
                + Math.cos(firstLat) * Math.cos(secondLat)
                * Math.sin(lonDelta / 2) * Math.sin(lonDelta / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static BigDecimal rounded(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDateTime timestamp(IngestMobileMoneyRequest request) {
        return request.executedAt() == null ? LocalDateTime.now() : request.executedAt();
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean same(String left, String right) {
        return present(left) && present(right) && left.equals(right);
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }

    private static Map<String, Object> evidence(Object... pairs) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i + 1] != null) evidence.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return evidence;
    }

    private static void add(List<SignalDraft> signals, String code, FinancialCrimeSignalType signalType,
            int scoreImpact, String description, Map<String, Object> evidence) {
        if (signals.stream().anyMatch(signal -> signal.code().equals(code))) return;
        String severity = scoreImpact >= 50 ? "CRITICAL" : scoreImpact >= 35 ? "HIGH"
                : scoreImpact >= 20 ? "MEDIUM" : "LOW";
        signals.add(new SignalDraft(code, signalType, severity, scoreImpact, description, evidence));
    }

    public record SignalDraft(String code, FinancialCrimeSignalType signalType, String severity,
            int scoreImpact, String description, Map<String, Object> evidence) {}

    public record Assessment(int riskScore, List<SignalDraft> signals) {}
}
