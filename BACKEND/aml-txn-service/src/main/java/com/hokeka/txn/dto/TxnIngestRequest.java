package com.hokeka.txn.dto;

import java.time.Instant;

public class TxnIngestRequest {
  public String txnId;
  public String pspId;
  public String panHash;
  public String merchantId;
  public Long amountCents;
  public String currency;
  public Instant txnTs;
}
