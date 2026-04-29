package com.hokeka.txn.service;

import com.hokeka.txn.cache.TxnHotCache;
import com.hokeka.txn.domain.Transaction;
import com.hokeka.txn.dto.TxnIngestRequest;
import com.hokeka.txn.events.TxnEventPublisher;
import com.hokeka.txn.repository.TxnRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TxnService {

  private final TxnRepository repo;
  private final TxnHotCache cache;
  private final TxnEventPublisher publisher;

  public TxnService(TxnRepository repo, TxnHotCache cache, TxnEventPublisher publisher) {
    this.repo = repo;
    this.cache = cache;
    this.publisher = publisher;
  }

  @Transactional
  public Transaction ingest(TxnIngestRequest req) {
    Transaction t = new Transaction();
    t.setTxnId(req.txnId == null || req.txnId.isBlank() ? UUID.randomUUID().toString() : req.txnId);
    t.setPspId(req.pspId);
    t.setPanHash(req.panHash);
    t.setMerchantId(req.merchantId);
    t.setAmountCents(req.amountCents);
    t.setCurrency(req.currency);
    t.setTxnTs(req.txnTs == null ? Instant.now() : req.txnTs);
    t.setIngestedTs(Instant.now());
    Transaction saved = repo.save(t);
    cache.upsert(saved);
    publisher.publishRaw(saved);
    return saved;
  }

  public Map<String, Object> getHotOrFallback(String pspId, String txnId) {
    Map<String, Object> hot = cache.get(pspId, txnId);
    if (hot != null) return hot;
    Optional<Transaction> fromDb = repo.findById(txnId);
    return fromDb.<Map<String, Object>>map(this::toView).orElse(null);
  }

  public Map<String, Object> getById(String txnId) {
    Optional<Transaction> fromDb = repo.findById(txnId);
    if (fromDb.isEmpty()) return null;
    Transaction t = fromDb.get();
    Map<String, Object> hot = cache.get(t.getPspId(), txnId);
    if (hot != null) return hot;
    return toView(t);
  }

  private Map<String, Object> toView(Transaction t) {
    Map<String, Object> m = new java.util.HashMap<>();
    m.put("txn_id", t.getTxnId());
    m.put("psp_id", t.getPspId());
    m.put("amount_c", t.getAmountCents());
    m.put("currency", t.getCurrency());
    m.put("risk", t.getRiskLevel() == null ? "" : t.getRiskLevel());
    m.put("decision", t.getDecision() == null ? "" : t.getDecision());
    m.put("txn_ts", t.getTxnTs() == null ? 0L : t.getTxnTs().toEpochMilli());
    m.put("source", "pg");
    return m;
  }

  public Page<Transaction> list(String pspId, int page, int size) {
    return repo.findByPspIdOrderByTxnTsDesc(pspId, PageRequest.of(page, size));
  }

  public Page<Transaction> listAll(int page, int size) {
    return repo.findAll(PageRequest.of(page, size));
  }
}
