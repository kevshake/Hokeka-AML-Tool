package com.posgateway.aml.service.search;

import com.posgateway.aml.dto.search.GlobalSearchDtos.GlobalSearchHit;
import com.posgateway.aml.dto.search.GlobalSearchDtos.GlobalSearchResponse;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tenant-scoped global search across the primary operator entities.
 */
@Service
public class GlobalSearchService {

    private static final int MAX_PER_ENTITY = 8;

    private final PspIsolationService pspIsolationService;
    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;
    private final ComplianceCaseRepository complianceCaseRepository;
    private final MerchantRepository merchantRepository;

    public GlobalSearchService(PspIsolationService pspIsolationService,
            TransactionRepository transactionRepository,
            AlertRepository alertRepository,
            ComplianceCaseRepository complianceCaseRepository,
            MerchantRepository merchantRepository) {
        this.pspIsolationService = pspIsolationService;
        this.transactionRepository = transactionRepository;
        this.alertRepository = alertRepository;
        this.complianceCaseRepository = complianceCaseRepository;
        this.merchantRepository = merchantRepository;
    }

    @Transactional(readOnly = true)
    public GlobalSearchResponse search(String rawQuery, int page, int size) {
        String query = normalize(rawQuery);
        if (query.isEmpty()) {
            return new GlobalSearchResponse(rawQuery != null ? rawQuery : "", 0, List.of());
        }

        Long pspId = pspIsolationService.getCurrentUserPspId();
        boolean platformAdmin = pspIsolationService.isPlatformAdministrator();
        int perEntity = Math.min(Math.max(size, 1), MAX_PER_ENTITY);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), perEntity);

        List<GlobalSearchHit> hits = new ArrayList<>();
        hits.addAll(mapTransactions(
                platformAdmin
                        ? transactionRepository.searchGlobal(query, pageable)
                        : transactionRepository.searchGlobalForPsp(pspId, query, pageable)));
        hits.addAll(mapAlerts(
                platformAdmin
                        ? alertRepository.searchGlobal(query, pageable)
                        : alertRepository.searchGlobalForPsp(pspId, query, pageable)));
        hits.addAll(mapCases(
                platformAdmin
                        ? complianceCaseRepository.searchGlobal(query, pageable)
                        : complianceCaseRepository.searchGlobalForPsp(pspId, query, pageable)));
        hits.addAll(mapMerchants(
                platformAdmin
                        ? merchantRepository.searchGlobal(query, pageable)
                        : merchantRepository.searchGlobalForPsp(pspId, query, pageable)));

        long total = hits.size();
        return new GlobalSearchResponse(query, total, hits);
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    private static List<GlobalSearchHit> mapTransactions(List<TransactionEntity> rows) {
        return rows.stream().map(txn -> new GlobalSearchHit(
                "TRANSACTION",
                String.valueOf(txn.getTxnId()),
                "Transaction #" + txn.getTxnId(),
                firstNonBlank(txn.getClientReference(), txn.getMerchantId(), txn.getPanHash()),
                txn.getDecision(),
                "/records/TRANSACTION/" + txn.getTxnId())).toList();
    }

    private static List<GlobalSearchHit> mapAlerts(List<Alert> rows) {
        return rows.stream().map(alert -> new GlobalSearchHit(
                "ALERT",
                String.valueOf(alert.getAlertId()),
                "Alert #" + alert.getAlertId(),
                firstNonBlank(alert.getReason(), alert.getSourceReference(), alert.getAction()),
                alert.getStatus(),
                "/records/ALERT/" + alert.getAlertId())).toList();
    }

    private static List<GlobalSearchHit> mapCases(List<ComplianceCase> rows) {
        return rows.stream().map(complianceCase -> new GlobalSearchHit(
                "CASE",
                String.valueOf(complianceCase.getId()),
                firstNonBlank(complianceCase.getCaseReference(), "Case #" + complianceCase.getId()),
                complianceCase.getDescription(),
                complianceCase.getStatus() != null ? complianceCase.getStatus().name() : null,
                "/records/CASE/" + complianceCase.getId())).toList();
    }

    private static List<GlobalSearchHit> mapMerchants(List<Merchant> rows) {
        return rows.stream().map(merchant -> new GlobalSearchHit(
                "MERCHANT",
                String.valueOf(merchant.getMerchantId()),
                firstNonBlank(merchant.getTradingName(), merchant.getLegalName()),
                merchant.getRegistrationNumber(),
                merchant.getStatus(),
                "/records/MERCHANT/" + merchant.getMerchantId())).toList();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
