package com.hokeka.txn.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions")
public class Transaction {

  @Id
  @Column(name = "txn_id", length = 64)
  private String txnId;

  @Column(name = "psp_id", length = 64, nullable = false)
  private String pspId;

  @Column(name = "pan_hash", length = 128)
  private String panHash;

  @Column(name = "merchant_id", length = 64)
  private String merchantId;

  @Column(name = "amount_cents", nullable = false)
  private Long amountCents;

  @Column(name = "currency", length = 3, nullable = false)
  private String currency;

  @Column(name = "risk_level", length = 16)
  private String riskLevel;

  @Column(name = "decision", length = 16)
  private String decision;

  @Column(name = "txn_ts", nullable = false)
  private Instant txnTs;

  @Column(name = "ingested_ts", nullable = false)
  private Instant ingestedTs;

  public String getTxnId() { return txnId; }
  public void setTxnId(String v) { this.txnId = v; }
  public String getPspId() { return pspId; }
  public void setPspId(String v) { this.pspId = v; }
  public String getPanHash() { return panHash; }
  public void setPanHash(String v) { this.panHash = v; }
  public String getMerchantId() { return merchantId; }
  public void setMerchantId(String v) { this.merchantId = v; }
  public Long getAmountCents() { return amountCents; }
  public void setAmountCents(Long v) { this.amountCents = v; }
  public String getCurrency() { return currency; }
  public void setCurrency(String v) { this.currency = v; }
  public String getRiskLevel() { return riskLevel; }
  public void setRiskLevel(String v) { this.riskLevel = v; }
  public String getDecision() { return decision; }
  public void setDecision(String v) { this.decision = v; }
  public Instant getTxnTs() { return txnTs; }
  public void setTxnTs(Instant v) { this.txnTs = v; }
  public Instant getIngestedTs() { return ingestedTs; }
  public void setIngestedTs(Instant v) { this.ingestedTs = v; }

  public BigDecimal amountAsDecimal() {
    return amountCents == null ? null : BigDecimal.valueOf(amountCents).movePointLeft(2);
  }
}
