package com.hokeka.txn.controller;

import com.hokeka.txn.domain.Transaction;
import com.hokeka.txn.dto.TxnIngestRequest;
import com.hokeka.txn.service.TxnService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/txn")
public class TxnController {

  private final TxnService svc;

  public TxnController(TxnService svc) {
    this.svc = svc;
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "OK", "service", "aml-txn-service");
  }

  @PostMapping("/ingest")
  public ResponseEntity<Transaction> ingest(@RequestBody TxnIngestRequest req) {
    return ResponseEntity.ok(svc.ingest(req));
  }

  @PostMapping("/ingest/batch")
  public Map<String, Object> ingestBatch(@RequestBody List<TxnIngestRequest> batch) {
    int n = 0;
    for (TxnIngestRequest r : batch) { svc.ingest(r); n++; }
    return Map.of("ingested", n);
  }

  @GetMapping("/{pspId}/{txnId}")
  public ResponseEntity<Map<String, Object>> getTxn(@PathVariable String pspId, @PathVariable String txnId) {
    Map<String, Object> out = svc.getHotOrFallback(pspId, txnId);
    return out == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(out);
  }

  @GetMapping("")
  public Page<Transaction> list(@RequestParam String pspId,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "50") int size) {
    return svc.list(pspId, page, size);
  }
}
