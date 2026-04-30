package com.posgateway.aml.service.reporting;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Regulatory Reporting Service
 * Generates regulatory reports (CTR, LCTR, IFTR)
 */
@Service
public class RegulatoryReportingService {

    private static final Logger logger = LoggerFactory.getLogger(RegulatoryReportingService.class);

    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final PspRepository pspRepository;
    private final com.posgateway.aml.service.security.PspIsolationService pspIsolationService;

    @Value("${regulatory.ctr.threshold:10000}")
    private BigDecimal ctrThreshold;

    @Value("${regulatory.lctr.threshold:100000}")
    private BigDecimal lctrThreshold;

    @Autowired
    public RegulatoryReportingService(TransactionRepository transactionRepository,
            MerchantRepository merchantRepository,
            PspRepository pspRepository,
            com.posgateway.aml.service.security.PspIsolationService pspIsolationService) {
        this.transactionRepository = transactionRepository;
        this.merchantRepository = merchantRepository;
        this.pspRepository = pspRepository;
        this.pspIsolationService = pspIsolationService;
    }

    /**
     * Generate Currency Transaction Report (CTR)
     * CTRs are required for transactions exceeding $10,000 in currency
     */
    public CurrencyTransactionReport generateCtr(LocalDateTime startDate, LocalDateTime endDate) {
        // Find transactions at or above CTR threshold
        List<TransactionEntity> transactions = findTransactionsAboveThreshold(ctrThreshold, startDate, endDate);

        CurrencyTransactionReport report = new CurrencyTransactionReport();
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        report.setThreshold(ctrThreshold);
        report.setTransactionCount(transactions.size());
        report.setTotalAmount(calculateTotalAmount(transactions));
        report.setTotalAmountByCurrency(calculateTotalAmountByCurrency(transactions));
        
        // Enrich transactions with detailed information
        List<CurrencyTransactionReport.TransactionDetail> transactionDetails = transactions.stream()
                .map(this::enrichTransactionForCtr)
                .collect(Collectors.toList());
        report.setTransactionDetails(transactionDetails);
        report.setTransactions(transactions); // Keep for backward compatibility

        logger.info("Generated CTR: {} transactions, total amount: {}",
                transactions.size(), report.getTotalAmount());
        return report;
    }

    /**
     * Generate Large Cash Transaction Report (LCTR)
     * LCTRs are required for transactions exceeding $100,000 in currency
     */
    public LargeCashTransactionReport generateLctr(LocalDateTime startDate, LocalDateTime endDate) {
        List<TransactionEntity> transactions = findTransactionsAboveThreshold(lctrThreshold, startDate, endDate);

        LargeCashTransactionReport report = new LargeCashTransactionReport();
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        report.setThreshold(lctrThreshold);
        report.setTransactionCount(transactions.size());
        report.setTotalAmount(calculateTotalAmount(transactions));
        report.setTotalAmountByCurrency(calculateTotalAmountByCurrency(transactions));
        
        // Enrich transactions with detailed information
        List<LargeCashTransactionReport.TransactionDetail> transactionDetails = transactions.stream()
                .map(this::enrichTransactionForLctr)
                .collect(Collectors.toList());
        report.setTransactionDetails(transactionDetails);
        report.setTransactions(transactions); // Keep for backward compatibility

        logger.info("Generated LCTR: {} transactions, total amount: {}",
                transactions.size(), report.getTotalAmount());
        return report;
    }

    /**
     * Generate International Funds Transfer Report (IFTR)
     * IFTRs are required for cross-border transactions and international wire transfers
     */
    /**
     * Generate International Funds Transfer Report (IFTR)
     * IFTRs are required for cross-border transactions and international wire transfers
     */
    public InternationalFundsTransferReport generateIftr(LocalDateTime startDate, LocalDateTime endDate) {
        // Find all transactions in date range, respecting PSP isolation
        Long pspId = pspIsolationService.getCurrentUserPspId();
        List<TransactionEntity> allTxns; 
        
        // Note: Ideally use a custom repository method findByTxnTsBetweenAndPspId
        // but for now reusing findAll and filtering or using internal helper logic
        if (pspId != null) {
            // Need a repository method for this efficiency, but effectively:
            // For now, let's fetch all and filter in memory if repo method missing, 
            // OR use the helper method pattern as below.
            // Using a helper fetching method for consistency:
            allTxns = fetchTransactions(startDate, endDate, pspId);
        } else {
            allTxns = SimpleTransactionFetcher(startDate, endDate);
        }

        List<TransactionEntity> iftrTxns = new ArrayList<>();

        for (TransactionEntity tx : allTxns) {
            // IFTR rule: Non-local currency OR Non-local country
            // For now assume "USD" is local and merchants have countries
            boolean isInternational = !"USD".equalsIgnoreCase(tx.getCurrency());

            if (!isInternational && tx.getMerchantId() != null) {
                try {
                    Long merchantId = Long.parseLong(tx.getMerchantId());
                    Merchant merchant = merchantRepository.findById(merchantId).orElse(null);
                    if (merchant != null) {
                        String merchantCountry = merchant.getAddressCountry() != null 
                                ? merchant.getAddressCountry() 
                                : merchant.getCountry();
                        // Flag any non-US merchant or non-USD currency as IFTR candidate
                        if (merchantCountry != null && !"US".equalsIgnoreCase(merchantCountry)) {
                            isInternational = true;
                        }
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
            }

            if (isInternational) {
                iftrTxns.add(tx);
            }
        }

        InternationalFundsTransferReport report = new InternationalFundsTransferReport();
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        report.setTransactionCount(iftrTxns.size());
        report.setTotalAmount(calculateTotalAmount(iftrTxns));
        report.setTotalAmountByCurrency(calculateTotalAmountByCurrency(iftrTxns));
        
        // Enrich transactions with detailed information
        List<InternationalFundsTransferReport.TransactionDetail> transactionDetails = iftrTxns.stream()
                .map(this::enrichTransactionForIftr)
                .collect(Collectors.toList());
        report.setTransactionDetails(transactionDetails);
        report.setTransactions(iftrTxns); // Keep for backward compatibility

        logger.info("Generated IFTR: {} transactions, total amount: {}",
                iftrTxns.size(), report.getTotalAmount());
        return report;
    }

    /**
     * Find transactions above threshold
     */
    /**
     * Find transactions above threshold
     */
    private List<TransactionEntity> findTransactionsAboveThreshold(BigDecimal threshold,
            LocalDateTime startDate,
            LocalDateTime endDate) {
        Long pspId = pspIsolationService.getCurrentUserPspId();
        BigDecimal thresholdCents = threshold.multiply(new BigDecimal("100"));
        
        List<TransactionEntity> allTransactions = fetchTransactions(startDate, endDate, pspId);

        return allTransactions.stream()
                .filter(tx -> tx.getAmountCents() != null &&
                        tx.getAmountCents() >= thresholdCents.longValue())
                .toList();
    }
    
    private List<TransactionEntity> fetchTransactions(LocalDateTime startDate, LocalDateTime endDate, Long pspId) {
        if (pspId != null) {
            // Use specific repository method if available, or findAll and filter
            // Ideally: transactionRepository.findByPspIdAndTxnTsBetween(pspId, startDate, endDate);
            // Assuming simplified retrieval for now to match interface:
            return transactionRepository.findAll().stream()
                    .filter(tx -> tx.getPspId() != null && tx.getPspId().equals(pspId))
                    .filter(tx -> tx.getTxnTs() != null && 
                            !tx.getTxnTs().isBefore(startDate) && 
                            !tx.getTxnTs().isAfter(endDate))
                    .collect(Collectors.toList());
        } else {
            return SimpleTransactionFetcher(startDate, endDate);
        }
    }
    
    private List<TransactionEntity> SimpleTransactionFetcher(LocalDateTime startDate, LocalDateTime endDate) {
         return transactionRepository.findAll().stream()
                .filter(tx -> tx.getTxnTs() != null &&
                        !tx.getTxnTs().isBefore(startDate) &&
                        !tx.getTxnTs().isAfter(endDate))
                .collect(Collectors.toList());
    }

    /**
     * Calculate total amount from transactions
     */
    private BigDecimal calculateTotalAmount(List<TransactionEntity> transactions) {
        return transactions.stream()
                .map(tx -> tx.getAmountCents() != null
                        ? BigDecimal.valueOf(tx.getAmountCents()).divide(new BigDecimal("100"))
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate total amounts grouped by currency.
     * NOTE: This does NOT do FX conversion; it simply clusters totals by currency code.
     */
    private Map<String, BigDecimal> calculateTotalAmountByCurrency(List<TransactionEntity> transactions) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (TransactionEntity tx : transactions) {
            String currency = normalizeCurrency(tx.getCurrency());
            BigDecimal amount = tx.getAmountCents() != null
                    ? BigDecimal.valueOf(tx.getAmountCents()).divide(new BigDecimal("100"))
                    : BigDecimal.ZERO;
            totals.merge(currency, amount, BigDecimal::add);
        }
        return totals;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null) return "USD";
        String c = currency.trim().toUpperCase();
        if (c.isEmpty()) return "USD";
        // Currency column is length=3; keep safe fallback anyway
        if (c.length() > 3) c = c.substring(0, 3);
        if (c.length() < 3) return "USD";
        return c;
    }

    /**
     * Enrich transaction with detailed information for CTR
     */
    private CurrencyTransactionReport.TransactionDetail enrichTransactionForCtr(TransactionEntity tx) {
        CurrencyTransactionReport.TransactionDetail detail = new CurrencyTransactionReport.TransactionDetail();
        detail.setTransactionId(tx.getTxnId() != null ? tx.getTxnId() : 0L);
        detail.setTransactionDate(tx.getTxnTs());
        detail.setAmount(BigDecimal.valueOf(tx.getAmountCents() != null ? tx.getAmountCents() : 0)
                .divide(new BigDecimal("100")));
        detail.setCurrency(tx.getCurrency() != null ? tx.getCurrency() : "USD");
        detail.setTerminalId(tx.getTerminalId());
        detail.setPanHash(tx.getPanHash()); // Masked account identifier
        
        // Merchant information
        if (tx.getMerchantId() != null) {
            try {
                Long merchantId = Long.parseLong(tx.getMerchantId());
                Merchant merchant = merchantRepository.findById(merchantId).orElse(null);
                if (merchant != null) {
                    detail.setMerchantName(merchant.getLegalName());
                    detail.setMerchantTradingName(merchant.getTradingName());
                    detail.setMerchantAddress(buildAddress(merchant));
                    detail.setMerchantCountry(merchant.getAddressCountry() != null 
                            ? merchant.getAddressCountry() 
                            : merchant.getCountry());
                    detail.setMerchantRegistrationNumber(merchant.getRegistrationNumber());
                    detail.setMerchantTaxId(merchant.getTaxId());
                    detail.setMerchantMcc(merchant.getMcc());
                }
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        
        // PSP/Financial Institution information
        Long pspId = tx.getPspId();
        if (pspId != null) {
            Psp psp = pspRepository.findById(pspId).orElse(null);
            if (psp != null) {
                detail.setFinancialInstitutionName(psp.getLegalName());
                detail.setFinancialInstitutionCode(psp.getPspCode());
                detail.setFinancialInstitutionAddress(psp.getContactAddress());
                detail.setFinancialInstitutionCountry(psp.getCountry());
            }
        }
        
        // Transaction type (derived from ISO message or default)
        detail.setTransactionType(determineTransactionType(tx));
        
        // IP Address and Device Fingerprint (for person conducting transaction)
        detail.setIpAddress(tx.getIpAddress());
        detail.setDeviceFingerprint(tx.getDeviceFingerprint());
        
        return detail;
    }

    /**
     * Enrich transaction with detailed information for LCTR
     */
    private LargeCashTransactionReport.TransactionDetail enrichTransactionForLctr(TransactionEntity tx) {
        LargeCashTransactionReport.TransactionDetail detail = new LargeCashTransactionReport.TransactionDetail();
        detail.setTransactionId(tx.getTxnId() != null ? tx.getTxnId() : 0L);
        detail.setTransactionDate(tx.getTxnTs());
        detail.setAmount(BigDecimal.valueOf(tx.getAmountCents() != null ? tx.getAmountCents() : 0)
                .divide(new BigDecimal("100")));
        detail.setCurrency(tx.getCurrency() != null ? tx.getCurrency() : "USD");
        detail.setTerminalId(tx.getTerminalId());
        detail.setPanHash(tx.getPanHash());
        
        // Merchant information
        if (tx.getMerchantId() != null) {
            try {
                Long merchantId = Long.parseLong(tx.getMerchantId());
                Merchant merchant = merchantRepository.findById(merchantId).orElse(null);
                if (merchant != null) {
                    detail.setMerchantName(merchant.getLegalName());
                    detail.setMerchantTradingName(merchant.getTradingName());
                    detail.setMerchantAddress(buildAddress(merchant));
                    detail.setMerchantCountry(merchant.getAddressCountry() != null 
                            ? merchant.getAddressCountry() 
                            : merchant.getCountry());
                    detail.setMerchantRegistrationNumber(merchant.getRegistrationNumber());
                    detail.setMerchantTaxId(merchant.getTaxId());
                }
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        
        // PSP/Financial Institution information
        Long pspId = tx.getPspId();
        if (pspId != null) {
            Psp psp = pspRepository.findById(pspId).orElse(null);
            if (psp != null) {
                detail.setFinancialInstitutionName(psp.getLegalName());
                detail.setFinancialInstitutionCode(psp.getPspCode());
                detail.setFinancialInstitutionAddress(psp.getContactAddress());
            }
        }
        
        detail.setTransactionType(determineTransactionType(tx));
        detail.setIpAddress(tx.getIpAddress());
        detail.setDeviceFingerprint(tx.getDeviceFingerprint());
        
        return detail;
    }

    /**
     * Enrich transaction with detailed information for IFTR
     */
    private InternationalFundsTransferReport.TransactionDetail enrichTransactionForIftr(TransactionEntity tx) {
        InternationalFundsTransferReport.TransactionDetail detail = new InternationalFundsTransferReport.TransactionDetail();
        detail.setTransactionId(tx.getTxnId() != null ? tx.getTxnId() : 0L);
        detail.setTransactionDate(tx.getTxnTs());
        detail.setAmount(BigDecimal.valueOf(tx.getAmountCents() != null ? tx.getAmountCents() : 0)
                .divide(new BigDecimal("100")));
        detail.setCurrency(tx.getCurrency() != null ? tx.getCurrency() : "USD");
        detail.setTerminalId(tx.getTerminalId());
        detail.setPanHash(tx.getPanHash());
        
        // Merchant information (destination/origin)
        String originCountry = "US"; // Default
        String destinationCountry = "US"; // Default
        
        if (tx.getMerchantId() != null) {
            try {
                Long merchantId = Long.parseLong(tx.getMerchantId());
                Merchant merchant = merchantRepository.findById(merchantId).orElse(null);
                if (merchant != null) {
                    detail.setMerchantName(merchant.getLegalName());
                    detail.setMerchantTradingName(merchant.getTradingName());
                    detail.setMerchantAddress(buildAddress(merchant));
                    destinationCountry = merchant.getAddressCountry() != null 
                            ? merchant.getAddressCountry() 
                            : merchant.getCountry();
                    detail.setDestinationCountry(destinationCountry);
                    detail.setMerchantRegistrationNumber(merchant.getRegistrationNumber());
                }
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        
        // PSP/Financial Institution information
        Long pspId = tx.getPspId();
        if (pspId != null) {
            Psp psp = pspRepository.findById(pspId).orElse(null);
            if (psp != null) {
                originCountry = psp.getCountry();
                detail.setOriginCountry(originCountry);
                detail.setFinancialInstitutionName(psp.getLegalName());
                detail.setFinancialInstitutionCode(psp.getPspCode());
                detail.setFinancialInstitutionAddress(psp.getContactAddress());
            }
        }
        
        // Determine if it's incoming or outgoing
        boolean isNonUsdCurrency = !"USD".equalsIgnoreCase(tx.getCurrency());
        boolean isCrossBorder = originCountry != null && destinationCountry != null 
                && !originCountry.equals(destinationCountry);
        
        detail.setTransferType(isCrossBorder ? "CROSS_BORDER" : (isNonUsdCurrency ? "FOREIGN_CURRENCY" : "DOMESTIC"));
        detail.setTransactionType(determineTransactionType(tx));
        detail.setIpAddress(tx.getIpAddress());
        
        return detail;
    }

    /**
     * Build full address string from merchant
     */
    private String buildAddress(Merchant merchant) {
        List<String> parts = new ArrayList<>();
        if (merchant.getAddressStreet() != null) parts.add(merchant.getAddressStreet());
        if (merchant.getAddressCity() != null) parts.add(merchant.getAddressCity());
        if (merchant.getAddressState() != null) parts.add(merchant.getAddressState());
        if (merchant.getAddressPostalCode() != null) parts.add(merchant.getAddressPostalCode());
        if (merchant.getAddressCountry() != null) parts.add(merchant.getAddressCountry());
        return String.join(", ", parts);
    }

    /**
     * Determine transaction type from ISO message or other indicators
     */
    private String determineTransactionType(TransactionEntity tx) {
        // In a real system, parse ISO message to determine type
        // For now, return a default based on available data
        if (tx.getIsoMsg() != null && tx.getIsoMsg().contains("0200")) {
            return "PURCHASE";
        } else if (tx.getIsoMsg() != null && tx.getIsoMsg().contains("0400")) {
            return "REVERSAL";
        } else if (tx.getAcquirerResponse() != null && tx.getAcquirerResponse().contains("APPROVED")) {
            return "PURCHASE";
        }
        return "UNKNOWN";
    }

    /**
     * Currency Transaction Report DTO
     */
    public static class CurrencyTransactionReport {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private BigDecimal threshold;
        private int transactionCount;
        private BigDecimal totalAmount;
        private Map<String, BigDecimal> totalAmountByCurrency;
        private List<TransactionEntity> transactions;

        // Getters and Setters
        public LocalDateTime getStartDate() {
            return startDate;
        }

        public void setStartDate(LocalDateTime startDate) {
            this.startDate = startDate;
        }

        public LocalDateTime getEndDate() {
            return endDate;
        }

        public void setEndDate(LocalDateTime endDate) {
            this.endDate = endDate;
        }

        public BigDecimal getThreshold() {
            return threshold;
        }

        public void setThreshold(BigDecimal threshold) {
            this.threshold = threshold;
        }

        public int getTransactionCount() {
            return transactionCount;
        }

        public void setTransactionCount(int transactionCount) {
            this.transactionCount = transactionCount;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }

        public Map<String, BigDecimal> getTotalAmountByCurrency() {
            return totalAmountByCurrency;
        }

        public void setTotalAmountByCurrency(Map<String, BigDecimal> totalAmountByCurrency) {
            this.totalAmountByCurrency = totalAmountByCurrency;
        }

        public List<TransactionEntity> getTransactions() {
            return transactions;
        }

        public void setTransactions(List<TransactionEntity> transactions) {
            this.transactions = transactions;
        }

        private List<TransactionDetail> transactionDetails;

        public List<TransactionDetail> getTransactionDetails() {
            return transactionDetails;
        }

        public void setTransactionDetails(List<TransactionDetail> transactionDetails) {
            this.transactionDetails = transactionDetails;
        }

        /**
         * Detailed transaction information for CTR
         */
        public static class TransactionDetail {
            private Long transactionId;
            private LocalDateTime transactionDate;
            private BigDecimal amount;
            private String currency;
            private String transactionType;
            private String terminalId;
            private String panHash; // Masked account identifier
            
            // Person conducting transaction
            private String ipAddress;
            private String deviceFingerprint;
            
            // Merchant information
            private String merchantName;
            private String merchantTradingName;
            private String merchantAddress;
            private String merchantCountry;
            private String merchantRegistrationNumber;
            private String merchantTaxId;
            private String merchantMcc;
            
            // Financial Institution (PSP) information
            private String financialInstitutionName;
            private String financialInstitutionCode;
            private String financialInstitutionAddress;
            private String financialInstitutionCountry;

            // Getters and Setters
            public Long getTransactionId() { return transactionId; }
            public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
            public LocalDateTime getTransactionDate() { return transactionDate; }
            public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }
            public BigDecimal getAmount() { return amount; }
            public void setAmount(BigDecimal amount) { this.amount = amount; }
            public String getCurrency() { return currency; }
            public void setCurrency(String currency) { this.currency = currency; }
            public String getTransactionType() { return transactionType; }
            public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
            public String getTerminalId() { return terminalId; }
            public void setTerminalId(String terminalId) { this.terminalId = terminalId; }
            public String getPanHash() { return panHash; }
            public void setPanHash(String panHash) { this.panHash = panHash; }
            public String getIpAddress() { return ipAddress; }
            public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
            public String getDeviceFingerprint() { return deviceFingerprint; }
            public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
            public String getMerchantName() { return merchantName; }
            public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
            public String getMerchantTradingName() { return merchantTradingName; }
            public void setMerchantTradingName(String merchantTradingName) { this.merchantTradingName = merchantTradingName; }
            public String getMerchantAddress() { return merchantAddress; }
            public void setMerchantAddress(String merchantAddress) { this.merchantAddress = merchantAddress; }
            public String getMerchantCountry() { return merchantCountry; }
            public void setMerchantCountry(String merchantCountry) { this.merchantCountry = merchantCountry; }
            public String getMerchantRegistrationNumber() { return merchantRegistrationNumber; }
            public void setMerchantRegistrationNumber(String merchantRegistrationNumber) { this.merchantRegistrationNumber = merchantRegistrationNumber; }
            public String getMerchantTaxId() { return merchantTaxId; }
            public void setMerchantTaxId(String merchantTaxId) { this.merchantTaxId = merchantTaxId; }
            public String getMerchantMcc() { return merchantMcc; }
            public void setMerchantMcc(String merchantMcc) { this.merchantMcc = merchantMcc; }
            public String getFinancialInstitutionName() { return financialInstitutionName; }
            public void setFinancialInstitutionName(String financialInstitutionName) { this.financialInstitutionName = financialInstitutionName; }
            public String getFinancialInstitutionCode() { return financialInstitutionCode; }
            public void setFinancialInstitutionCode(String financialInstitutionCode) { this.financialInstitutionCode = financialInstitutionCode; }
            public String getFinancialInstitutionAddress() { return financialInstitutionAddress; }
            public void setFinancialInstitutionAddress(String financialInstitutionAddress) { this.financialInstitutionAddress = financialInstitutionAddress; }
            public String getFinancialInstitutionCountry() { return financialInstitutionCountry; }
            public void setFinancialInstitutionCountry(String financialInstitutionCountry) { this.financialInstitutionCountry = financialInstitutionCountry; }
        }
    }

    /**
     * Large Cash Transaction Report DTO
     */
    public static class LargeCashTransactionReport {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private BigDecimal threshold;
        private int transactionCount;
        private BigDecimal totalAmount;
        private Map<String, BigDecimal> totalAmountByCurrency;
        private List<TransactionEntity> transactions;

        // Getters and Setters
        public LocalDateTime getStartDate() {
            return startDate;
        }

        public void setStartDate(LocalDateTime startDate) {
            this.startDate = startDate;
        }

        public LocalDateTime getEndDate() {
            return endDate;
        }

        public void setEndDate(LocalDateTime endDate) {
            this.endDate = endDate;
        }

        public BigDecimal getThreshold() {
            return threshold;
        }

        public void setThreshold(BigDecimal threshold) {
            this.threshold = threshold;
        }

        public int getTransactionCount() {
            return transactionCount;
        }

        public void setTransactionCount(int transactionCount) {
            this.transactionCount = transactionCount;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }

        public Map<String, BigDecimal> getTotalAmountByCurrency() {
            return totalAmountByCurrency;
        }

        public void setTotalAmountByCurrency(Map<String, BigDecimal> totalAmountByCurrency) {
            this.totalAmountByCurrency = totalAmountByCurrency;
        }

        public List<TransactionEntity> getTransactions() {
            return transactions;
        }

        public void setTransactions(List<TransactionEntity> transactions) {
            this.transactions = transactions;
        }

        private List<TransactionDetail> transactionDetails;

        public List<TransactionDetail> getTransactionDetails() {
            return transactionDetails;
        }

        public void setTransactionDetails(List<TransactionDetail> transactionDetails) {
            this.transactionDetails = transactionDetails;
        }

        /**
         * Detailed transaction information for LCTR
         */
        public static class TransactionDetail {
            private Long transactionId;
            private LocalDateTime transactionDate;
            private BigDecimal amount;
            private String currency;
            private String transactionType;
            private String terminalId;
            private String panHash;
            private String ipAddress;
            private String deviceFingerprint;
            private String merchantName;
            private String merchantTradingName;
            private String merchantAddress;
            private String merchantCountry;
            private String merchantRegistrationNumber;
            private String merchantTaxId;
            private String financialInstitutionName;
            private String financialInstitutionCode;
            private String financialInstitutionAddress;

            // Getters and Setters
            public Long getTransactionId() { return transactionId; }
            public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
            public LocalDateTime getTransactionDate() { return transactionDate; }
            public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }
            public BigDecimal getAmount() { return amount; }
            public void setAmount(BigDecimal amount) { this.amount = amount; }
            public String getCurrency() { return currency; }
            public void setCurrency(String currency) { this.currency = currency; }
            public String getTransactionType() { return transactionType; }
            public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
            public String getTerminalId() { return terminalId; }
            public void setTerminalId(String terminalId) { this.terminalId = terminalId; }
            public String getPanHash() { return panHash; }
            public void setPanHash(String panHash) { this.panHash = panHash; }
            public String getIpAddress() { return ipAddress; }
            public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
            public String getDeviceFingerprint() { return deviceFingerprint; }
            public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
            public String getMerchantName() { return merchantName; }
            public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
            public String getMerchantTradingName() { return merchantTradingName; }
            public void setMerchantTradingName(String merchantTradingName) { this.merchantTradingName = merchantTradingName; }
            public String getMerchantAddress() { return merchantAddress; }
            public void setMerchantAddress(String merchantAddress) { this.merchantAddress = merchantAddress; }
            public String getMerchantCountry() { return merchantCountry; }
            public void setMerchantCountry(String merchantCountry) { this.merchantCountry = merchantCountry; }
            public String getMerchantRegistrationNumber() { return merchantRegistrationNumber; }
            public void setMerchantRegistrationNumber(String merchantRegistrationNumber) { this.merchantRegistrationNumber = merchantRegistrationNumber; }
            public String getMerchantTaxId() { return merchantTaxId; }
            public void setMerchantTaxId(String merchantTaxId) { this.merchantTaxId = merchantTaxId; }
            public String getFinancialInstitutionName() { return financialInstitutionName; }
            public void setFinancialInstitutionName(String financialInstitutionName) { this.financialInstitutionName = financialInstitutionName; }
            public String getFinancialInstitutionCode() { return financialInstitutionCode; }
            public void setFinancialInstitutionCode(String financialInstitutionCode) { this.financialInstitutionCode = financialInstitutionCode; }
            public String getFinancialInstitutionAddress() { return financialInstitutionAddress; }
            public void setFinancialInstitutionAddress(String financialInstitutionAddress) { this.financialInstitutionAddress = financialInstitutionAddress; }
        }
    }

    /**
     * International Funds Transfer Report DTO
     */
    public static class InternationalFundsTransferReport {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private int transactionCount;
        private BigDecimal totalAmount;
        private Map<String, BigDecimal> totalAmountByCurrency;
        private List<TransactionEntity> transactions;

        // Getters and Setters
        public LocalDateTime getStartDate() {
            return startDate;
        }

        public void setStartDate(LocalDateTime startDate) {
            this.startDate = startDate;
        }

        public LocalDateTime getEndDate() {
            return endDate;
        }

        public void setEndDate(LocalDateTime endDate) {
            this.endDate = endDate;
        }

        public int getTransactionCount() {
            return transactionCount;
        }

        public void setTransactionCount(int transactionCount) {
            this.transactionCount = transactionCount;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }

        public Map<String, BigDecimal> getTotalAmountByCurrency() {
            return totalAmountByCurrency;
        }

        public void setTotalAmountByCurrency(Map<String, BigDecimal> totalAmountByCurrency) {
            this.totalAmountByCurrency = totalAmountByCurrency;
        }

        public List<TransactionEntity> getTransactions() {
            return transactions;
        }

        public void setTransactions(List<TransactionEntity> transactions) {
            this.transactions = transactions;
        }

        private List<TransactionDetail> transactionDetails;

        public List<TransactionDetail> getTransactionDetails() {
            return transactionDetails;
        }

        public void setTransactionDetails(List<TransactionDetail> transactionDetails) {
            this.transactionDetails = transactionDetails;
        }

        /**
         * Detailed transaction information for IFTR
         */
        public static class TransactionDetail {
            private Long transactionId;
            private LocalDateTime transactionDate;
            private BigDecimal amount;
            private String currency;
            private String transactionType;
            private String transferType; // CROSS_BORDER, FOREIGN_CURRENCY, DOMESTIC
            private String terminalId;
            private String panHash;
            private String ipAddress;
            
            // Origin information
            private String originCountry;
            private String financialInstitutionName;
            private String financialInstitutionCode;
            private String financialInstitutionAddress;
            
            // Destination information
            private String destinationCountry;
            private String merchantName;
            private String merchantTradingName;
            private String merchantAddress;
            private String merchantRegistrationNumber;

            // Getters and Setters
            public Long getTransactionId() { return transactionId; }
            public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
            public LocalDateTime getTransactionDate() { return transactionDate; }
            public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }
            public BigDecimal getAmount() { return amount; }
            public void setAmount(BigDecimal amount) { this.amount = amount; }
            public String getCurrency() { return currency; }
            public void setCurrency(String currency) { this.currency = currency; }
            public String getTransactionType() { return transactionType; }
            public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
            public String getTransferType() { return transferType; }
            public void setTransferType(String transferType) { this.transferType = transferType; }
            public String getTerminalId() { return terminalId; }
            public void setTerminalId(String terminalId) { this.terminalId = terminalId; }
            public String getPanHash() { return panHash; }
            public void setPanHash(String panHash) { this.panHash = panHash; }
            public String getIpAddress() { return ipAddress; }
            public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
            public String getOriginCountry() { return originCountry; }
            public void setOriginCountry(String originCountry) { this.originCountry = originCountry; }
            public String getFinancialInstitutionName() { return financialInstitutionName; }
            public void setFinancialInstitutionName(String financialInstitutionName) { this.financialInstitutionName = financialInstitutionName; }
            public String getFinancialInstitutionCode() { return financialInstitutionCode; }
            public void setFinancialInstitutionCode(String financialInstitutionCode) { this.financialInstitutionCode = financialInstitutionCode; }
            public String getFinancialInstitutionAddress() { return financialInstitutionAddress; }
            public void setFinancialInstitutionAddress(String financialInstitutionAddress) { this.financialInstitutionAddress = financialInstitutionAddress; }
            public String getDestinationCountry() { return destinationCountry; }
            public void setDestinationCountry(String destinationCountry) { this.destinationCountry = destinationCountry; }
            public String getMerchantName() { return merchantName; }
            public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
            public String getMerchantTradingName() { return merchantTradingName; }
            public void setMerchantTradingName(String merchantTradingName) { this.merchantTradingName = merchantTradingName; }
            public String getMerchantAddress() { return merchantAddress; }
            public void setMerchantAddress(String merchantAddress) { this.merchantAddress = merchantAddress; }
            public String getMerchantRegistrationNumber() { return merchantRegistrationNumber; }
            public void setMerchantRegistrationNumber(String merchantRegistrationNumber) { this.merchantRegistrationNumber = merchantRegistrationNumber; }
        }
    }
}
