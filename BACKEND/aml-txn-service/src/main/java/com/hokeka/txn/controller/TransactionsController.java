package com.hokeka.txn.controller;

import com.hokeka.txn.domain.Transaction;
import com.hokeka.txn.service.TxnService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Canonical transaction read API per HOK-76.
 * Aerospike-first reads with Postgres fallback.
 * Replaces legacy {@link TxnController} ({@code /v1/txn}) for new integrations.
 */
@RestController
@RequestMapping("/transactions")
public class TransactionsController {

  private final TxnService svc;

  public TransactionsController(TxnService svc) {
    this.svc = svc;
  }

  @GetMapping("")
  public Page<Transaction> list(@RequestParam(required = false) String pspId,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "50") int size) {
    return pspId == null || pspId.isBlank()
        ? svc.listAll(page, size)
        : svc.list(pspId, page, size);
  }

  @GetMapping("/{id}")
  public ResponseEntity<Map<String, Object>> getById(@PathVariable String id) {
    Map<String, Object> out = svc.getById(id);
    return out == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(out);
  }
}
