package com.hokeka.txn.events;

import com.hokeka.txn.domain.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TxnEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(TxnEventPublisher.class);

  private final KafkaTemplate<String, Object> kafka;
  private final String rawTopic;
  private final String scoredTopic;
  private final String decisionTopic;

  public TxnEventPublisher(KafkaTemplate<String, Object> kafka,
                           @Value("${aml.kafka.topics.raw:transactions.ingested}") String rawTopic,
                           @Value("${aml.kafka.topics.scored:aml.txn.scored}") String scoredTopic,
                           @Value("${aml.kafka.topics.decision:aml.txn.decision}") String decisionTopic) {
    this.kafka = kafka;
    this.rawTopic = rawTopic;
    this.scoredTopic = scoredTopic;
    this.decisionTopic = decisionTopic;
  }

  public void publishRaw(Transaction t) {
    Map<String, Object> evt = Map.of(
        "txnId", t.getTxnId(),
        "pspId", t.getPspId(),
        "panHash", nullSafe(t.getPanHash()),
        "merchantId", nullSafe(t.getMerchantId()),
        "amountCents", t.getAmountCents(),
        "currency", t.getCurrency(),
        "txnTs", t.getTxnTs() == null ? 0L : t.getTxnTs().toEpochMilli()
    );
    kafka.send(rawTopic, t.getPspId(), evt).whenComplete((res, ex) -> {
      if (ex != null) log.warn("Failed to publish {} for txn {}: {}", rawTopic, t.getTxnId(), ex.getMessage());
    });
  }

  private static String nullSafe(String s) { return s == null ? "" : s; }
}
