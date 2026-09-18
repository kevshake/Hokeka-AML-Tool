package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.billing.CurrencyRate;
import com.posgateway.aml.repository.CurrencyRateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class RegulatoryExchangeRateService {

    private final CurrencyRateRepository repository;
    private final Duration maximumRateAge;

    public RegulatoryExchangeRateService(
            CurrencyRateRepository repository,
            @Value("${compliance.regulatory-fx.max-age-hours:72}") long maximumRateAgeHours) {
        this.repository = repository;
        this.maximumRateAge = Duration.ofHours(Math.max(1L, maximumRateAgeHours));
    }

    @Transactional(readOnly = true)
    public ConversionResult convertToUsd(BigDecimal amount, String currency, LocalDateTime asOf) {
        LocalDateTime evaluationTime = asOf != null ? asOf : LocalDateTime.now();
        if (amount == null || amount.signum() < 0) {
            return ConversionResult.unavailable("INVALID_AMOUNT");
        }
        String code = normalizeCurrency(currency);
        if (code == null) {
            return ConversionResult.unavailable("INVALID_CURRENCY");
        }
        if ("USD".equals(code)) {
            return ConversionResult.available(
                    amount.setScale(4, RoundingMode.HALF_UP),
                    BigDecimal.ONE,
                    "USD_PARITY",
                    evaluationTime);
        }

        CurrencyRate rate = repository.findById(code).orElse(null);
        if (rate == null) return ConversionResult.unavailable("RATE_NOT_FOUND");
        if (!Boolean.TRUE.equals(rate.getIsActive())) return ConversionResult.unavailable("RATE_INACTIVE");
        if (!rate.isRegulatoryApproved()) return ConversionResult.unavailable("RATE_NOT_APPROVED");
        if (rate.getRateToUsd() == null || rate.getRateToUsd().signum() <= 0) {
            return ConversionResult.unavailable("RATE_INVALID");
        }
        if (rate.getRateSource() == null || rate.getRateSource().isBlank()) {
            return ConversionResult.unavailable("RATE_SOURCE_MISSING");
        }
        if (rate.getEffectiveAt() == null || rate.getEffectiveAt().isAfter(evaluationTime)) {
            return ConversionResult.unavailable("RATE_NOT_EFFECTIVE");
        }
        if (rate.getExpiresAt() != null && rate.getExpiresAt().isBefore(evaluationTime)) {
            return ConversionResult.unavailable("RATE_EXPIRED");
        }
        if (rate.getEffectiveAt().isBefore(evaluationTime.minus(maximumRateAge))) {
            return ConversionResult.unavailable("RATE_STALE");
        }

        return ConversionResult.available(
                amount.multiply(rate.getRateToUsd()).setScale(4, RoundingMode.HALF_UP),
                rate.getRateToUsd(),
                rate.getRateSource(),
                rate.getEffectiveAt());
    }

    @Transactional(readOnly = true)
    public List<CurrencyRate> listRates() {
        return repository.findAll().stream()
                .sorted(java.util.Comparator.comparing(CurrencyRate::getCurrencyCode))
                .toList();
    }

    @Transactional
    public CurrencyRate approveRate(String currency, BigDecimal rateToUsd, String source,
                                    LocalDateTime effectiveAt, LocalDateTime expiresAt,
                                    String actor) {
        String code = requireCurrency(currency);
        if (rateToUsd == null || rateToUsd.signum() <= 0) {
            throw new IllegalArgumentException("rateToUsd must be greater than zero");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("rate source is required");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effective = effectiveAt != null ? effectiveAt : now;
        if (expiresAt != null && !expiresAt.isAfter(effective)) {
            throw new IllegalArgumentException("expiresAt must be after effectiveAt");
        }

        CurrencyRate rate = repository.findById(code).orElseGet(CurrencyRate::new);
        rate.setCurrencyCode(code);
        rate.setRateToUsd(rateToUsd);
        rate.setRateSource(source.trim());
        rate.setEffectiveAt(effective);
        rate.setExpiresAt(expiresAt);
        rate.setIsActive(true);
        rate.setRegulatoryApproved(true);
        rate.setApprovedBy(actor);
        rate.setApprovedAt(now);
        return repository.save(rate);
    }

    @Transactional
    public CurrencyRate revokeApproval(String currency, String actor) {
        CurrencyRate rate = repository.findById(requireCurrency(currency))
                .orElseThrow(() -> new IllegalArgumentException("currency rate not found"));
        rate.setRegulatoryApproved(false);
        rate.setApprovedBy(actor);
        rate.setApprovedAt(LocalDateTime.now());
        return repository.save(rate);
    }

    private String requireCurrency(String currency) {
        String normalized = normalizeCurrency(currency);
        if (normalized == null) {
            throw new IllegalArgumentException("currency must be a three-letter ISO 4217 code");
        }
        return normalized;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null) return null;
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z]{3}") ? normalized : null;
    }

    public record ConversionResult(
            boolean available,
            BigDecimal amountUsd,
            BigDecimal rateToUsd,
            String source,
            LocalDateTime effectiveAt,
            String unavailableReason) {

        static ConversionResult available(BigDecimal amountUsd, BigDecimal rateToUsd,
                                          String source, LocalDateTime effectiveAt) {
            return new ConversionResult(true, amountUsd, rateToUsd, source, effectiveAt, null);
        }

        static ConversionResult unavailable(String reason) {
            return new ConversionResult(false, null, null, null, null, reason);
        }
    }
}
