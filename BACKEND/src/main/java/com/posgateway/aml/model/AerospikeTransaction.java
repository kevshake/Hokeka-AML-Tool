package com.posgateway.aml.model;

import com.aerospike.client.Record;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Transaction model optimized for Aerospike storage and retrieval.
 * Uses pre-calculated composite key bins to enable secondary-index lookups
 * without full namespace scans.
 */
public class AerospikeTransaction {

    private String txnId;
    private BigDecimal amount;
    private String currency;
    private String merchantId;
    private String accountNumber; // PAN hash in production
    private String status;
    private String countryCode;
    private Long timestamp;

    // Composite search key bins (pre-encoded WHERE clauses)
    private String merchantDateKey;   // merchantId + "#" + yyyyMMdd
    private String cardDateKey;       // accountNumber + "#" + yyyyMMdd
    private String statusCountryKey;  // status + "#" + countryCode
    private String merchantStatusKey; // merchantId + "#" + status

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public AerospikeTransaction() {}

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    public static AerospikeTransaction from(Transaction t) {
        AerospikeTransaction at = new AerospikeTransaction();
        at.txnId = t.getTransactionId();
        at.amount = t.getAmount();
        at.currency = t.getCurrencyCode();
        at.merchantId = t.getMerchantId();
        at.accountNumber = t.getAccountNumber();
        at.status = t.getStatus() != null ? t.getStatus().name() : "PENDING";
        at.countryCode = t.getCountryCode();
        at.timestamp = System.currentTimeMillis();

        String dateStr = t.getTransactionTimestamp() != null
                ? t.getTransactionTimestamp().format(DATE_FMT)
                : LocalDateTime.now().format(DATE_FMT);

        at.merchantDateKey   = at.merchantId + "#" + dateStr;
        at.cardDateKey        = at.accountNumber + "#" + dateStr;
        at.statusCountryKey  = at.status + "#" + (at.countryCode != null ? at.countryCode : "UNKNOWN");
        at.merchantStatusKey = at.merchantId + "#" + at.status;
        return at;
    }

    public static AerospikeTransaction from(com.posgateway.aml.entity.TransactionEntity entity) {
        AerospikeTransaction at = new AerospikeTransaction();
        at.txnId = String.valueOf(entity.getTxnId());
        at.amount = entity.getAmountCents() != null
                ? BigDecimal.valueOf(entity.getAmountCents()).divide(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        at.currency = entity.getCurrency();
        at.merchantId = entity.getMerchantId();
        at.accountNumber = entity.getPanHash();
        at.status = entity.getDecision() != null ? entity.getDecision() : "PENDING";
        at.countryCode = entity.getMerchantCountry();

        LocalDateTime ts = entity.getTxnTs() != null ? entity.getTxnTs() : LocalDateTime.now();
        at.timestamp = java.sql.Timestamp.valueOf(ts).getTime();

        String dateStr = ts.format(DATE_FMT);
        String mid = at.merchantId != null ? at.merchantId : "UNKNOWN";
        String pan = at.accountNumber != null ? at.accountNumber : "UNKNOWN";
        String cc  = at.countryCode != null ? at.countryCode : "UNKNOWN";

        at.merchantDateKey   = mid + "#" + dateStr;
        at.cardDateKey        = pan + "#" + dateStr;
        at.statusCountryKey  = at.status + "#" + cc;
        at.merchantStatusKey = mid + "#" + at.status;
        return at;
    }

    /** Deserialise an Aerospike Record back to a model object. */
    public static AerospikeTransaction fromRecord(Record record, String txnId) {
        AerospikeTransaction t = new AerospikeTransaction();
        t.txnId = txnId;
        t.currency        = record.getString("currency");
        t.merchantId      = record.getString("merchantId");
        t.accountNumber   = record.getString("accountNumber");
        t.status          = record.getString("status");
        t.countryCode     = record.getString("country");
        t.timestamp       = record.getLong("timestamp");
        t.merchantDateKey  = record.getString("merchantDateKey");
        t.cardDateKey       = record.getString("cardDateKey");
        t.statusCountryKey = record.getString("statusCountryKey");
        t.merchantStatusKey = record.getString("merchantStatusKey");
        Object amountObj = record.getValue("amount");
        if (amountObj instanceof Number) {
            t.amount = BigDecimal.valueOf(((Number) amountObj).doubleValue());
        }
        return t;
    }

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public String getMerchantDateKey() { return merchantDateKey; }
    public void setMerchantDateKey(String merchantDateKey) { this.merchantDateKey = merchantDateKey; }

    public String getCardDateKey() { return cardDateKey; }
    public void setCardDateKey(String cardDateKey) { this.cardDateKey = cardDateKey; }

    public String getStatusCountryKey() { return statusCountryKey; }
    public void setStatusCountryKey(String statusCountryKey) { this.statusCountryKey = statusCountryKey; }

    public String getMerchantStatusKey() { return merchantStatusKey; }
    public void setMerchantStatusKey(String merchantStatusKey) { this.merchantStatusKey = merchantStatusKey; }
}
